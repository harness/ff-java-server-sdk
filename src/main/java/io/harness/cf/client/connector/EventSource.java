package io.harness.cf.client.connector;

import com.google.gson.Gson;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.logger.LogUtil;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class EventSource extends EventSourceListener implements AutoCloseable, Service {

  private final Updater updater;
  private final Gson gson = new Gson();
  private final Request.Builder builder;
  private final int retryBackoffDelay;
  private final HttpLoggingInterceptor loggingInterceptor;
  private final okhttp3.sse.EventSource.Factory sseFactory;
  private final OkHttpClient streamClient;
  private okhttp3.sse.EventSource sse;

  static {
    LogUtil.setSystemProps();
  }

  public EventSource(
      @NonNull String url,
      Map<String, String> headers,
      @NonNull Updater updater,
      long sseReadTimeoutMins)
      throws ConnectorException {
    this(url, headers, updater, sseReadTimeoutMins, 2_000, null);
  }

  EventSource(
      @NonNull String url,
      Map<String, String> headers,
      @NonNull Updater updater,
      long sseReadTimeoutMins,
      int retryBackoffDelay,
      List<X509Certificate> trustedCAs)
      throws ConnectorException {
    this.updater = updater;
    this.retryBackoffDelay = retryBackoffDelay;
    this.streamClient = makeStreamClient(sseReadTimeoutMins, trustedCAs);
    this.sseFactory = EventSources.createFactory(this.streamClient);
    this.loggingInterceptor = new HttpLoggingInterceptor();

    builder = new Request.Builder().url(url);
    headers.put("User-Agent", "JavaSDK " + io.harness.cf.Version.VERSION);
    headers.forEach(builder::header);
    updater.onReady();
    log.info("EventSource initialized with url {} and headers {}", url, headers);
  }

  protected OkHttpClient makeStreamClient(long sseReadTimeoutMins, List<X509Certificate> trustedCAs)
      throws ConnectorException {
    OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder()
            .readTimeout(sseReadTimeoutMins, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true);

    setupTls(httpClientBuilder, trustedCAs);

    if (log.isDebugEnabled()) {
      httpClientBuilder.addInterceptor(loggingInterceptor);
    } else {
      httpClientBuilder.interceptors().remove(loggingInterceptor);
    }

    httpClientBuilder.addInterceptor(
        chain -> {
          final Request request =
              chain
                  .request()
                  .newBuilder()
                  .addHeader("X-Request-ID", UUID.randomUUID().toString())
                  .build();
          log.info("interceptor: requesting url {}", request.url().url());

          return chain.proceed(request);
        });

    httpClientBuilder.addInterceptor(makeRetryInterceptor());
    log.info("stream http client definition complete");
    return httpClientBuilder.build();
  }

  private Interceptor makeRetryInterceptor() {
    return new Interceptor() {
      @NotNull
      @Override
      public Response intercept(@NotNull Chain chain) throws IOException {
        int tryCount = 1;
        int maxTryCount = 3;
        boolean successful;
        Response response = null;
        String msg = "";
        do {
          try {
            if (response != null) response.close();

            response = chain.proceed(chain.request());
            successful = response.isSuccessful();
            if (!successful) {
              msg = String.format("httpCode=%d %s", response.code(), response.message());
              if (!shouldRetryHttpErrorCode(response.code())) {
                return response;
              }
            } else if (tryCount > 1) {
              log.info(
                  "Connection to {} was successful after {} attempts",
                  chain.request().url(),
                  tryCount);
            }

          } catch (Exception ex) {
            log.trace("Error while attempting to make request", ex);
            msg = ex.getMessage();
            response = makeErrorResp(chain, msg);
            successful = false;
            if (!shouldRetryException(ex)) {
              return response;
            }
          }
          if (!successful) {
            final boolean limitReached = tryCount > maxTryCount;
            log.warn(
                "Request attempt {} to {} was not successful, [{}]{}",
                tryCount,
                chain.request().url(),
                msg,
                limitReached
                    ? ", retry limited reached"
                    : String.format(", retrying in %dms", retryBackoffDelay * tryCount));
            updater.onError();
            if (!limitReached) {
              sleep((long) retryBackoffDelay * tryCount);
            }
          }
        } while (!successful && tryCount++ <= maxTryCount);

        return response;
      }

      private boolean shouldRetryException(Exception ex) {
        return true;
      }

      private Response makeErrorResp(Chain chain, String msg) {
        return new Response.Builder()
            .code(404) /* dummy response: real reason is in the message */
            .request(chain.request())
            .protocol(Protocol.HTTP_2)
            .message(msg)
            .body(ResponseBody.create("", MediaType.parse("text/plain")))
            .build();
      }

      private void sleep(long delayMs) {
        try {
          TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
          log.debug("Retry backoff interrupted", e);
          Thread.currentThread().interrupt();
        }
      }
    };
  }

  private void setupTls(OkHttpClient.Builder httpClientBuilder, List<X509Certificate> trustedCAs)
      throws ConnectorException {

    try {
      if (trustedCAs != null && !trustedCAs.isEmpty()) {

        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        for (int i = 0; i < trustedCAs.size(); i++) {
          keyStore.setCertificateEntry("ca" + i, trustedCAs.get(i));
        }

        final TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, new SecureRandom());

        httpClientBuilder.sslSocketFactory(
            sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0]);
      }
    } catch (GeneralSecurityException | IOException ex) {
      String msg = "Failed to setup TLS on SSE endpoint: " + ex.getMessage();
      log.warn(msg, ex);
      throw new ConnectorException(msg, true, ex);
    }
  }

  @Override
  public void onOpen(okhttp3.sse.EventSource eventSource, Response response) {
    log.info("EventSource onOpen");
    if (updater != null) {
      log.info("EventSource connected!");
      updater.onConnected();
    }
  }

  @Override
  public void onEvent(okhttp3.sse.EventSource eventSource, String id, String type, String data) {
    log.info("EventSource onMessage {}", data);
    Message msg = gson.fromJson(data, Message.class);
    updater.update(msg);
  }

  private boolean shouldRetryHttpErrorCode(int httpCode) {
    if (httpCode == 501) return false;

    return httpCode == 429 || httpCode >= 500;
  }

  @Override
  public void onClosed(okhttp3.sse.EventSource eventSource) {
    log.info("EventSource onClosed - disconnected");
    updater.onDisconnected();
  }

  @Override
  public void start() {
    log.info("Starting EventSource service.");
    sse = sseFactory.newEventSource(builder.build(), this);
  }

  @Override
  public void stop() {
    log.info("Stopping EventSource service.");
    sse.cancel();
  }

  public void close() {
    stop();
    this.streamClient.connectionPool().evictAll();
    log.info("EventSource closed");
  }
}
