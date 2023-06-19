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
import okio.BufferedSource;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class EventSource implements Callback, AutoCloseable, Service {

  private final Updater updater;
  private final Gson gson = new Gson();
  private final HttpLoggingInterceptor loggingInterceptor;
  private final long retryBackoffDelay;
  private OkHttpClient streamClient;
  private Call call;

  private final String url;
  private final Map<String, String> headers;
  private final long sseReadTimeoutMins;
  private final List<X509Certificate> trustedCAs;

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
      List<X509Certificate> trustedCAs) {
    this.url = url;
    this.headers = headers;
    this.updater = updater;
    this.sseReadTimeoutMins = sseReadTimeoutMins;
    this.retryBackoffDelay = retryBackoffDelay;
    this.trustedCAs = trustedCAs;
    this.loggingInterceptor = new HttpLoggingInterceptor();
  }

  protected OkHttpClient makeStreamClient(long sseReadTimeoutMins, List<X509Certificate> trustedCAs)
      throws ConnectorException {
    OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder()
            .eventListener(EventListener.NONE)
            .readTimeout(sseReadTimeoutMins, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true);

    setupTls(httpClientBuilder, trustedCAs);

    if (log.isDebugEnabled()) {
      httpClientBuilder.addInterceptor(loggingInterceptor);
    } else {
      httpClientBuilder.interceptors().remove(loggingInterceptor);
    }

    httpClientBuilder.addInterceptor(makeRetryInterceptor());
    return httpClientBuilder.build();
  }

  private Interceptor makeRetryInterceptor() {
    return new Interceptor() {
      @NotNull
      @Override
      public Response intercept(@NotNull Chain chain) throws IOException {
        int tryCount = 1;
        int maxTryCount = 5;
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

      private boolean shouldRetryHttpErrorCode(int httpCode) {
        if (httpCode == 501) return false;

        return httpCode == 429 || httpCode >= 500;
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
  public void start() throws ConnectorException, InterruptedException {
    log.info("EventSource connecting with url {} and headers {}", url, headers);

    this.streamClient = makeStreamClient(sseReadTimeoutMins, trustedCAs);

    final Request.Builder builder =
        new Request.Builder()
            .url(url)
            .addHeader("User-Agent", "JavaSDK " + io.harness.cf.Version.VERSION)
            .addHeader("X-Request-ID", UUID.randomUUID().toString());

    headers.forEach(builder::header);

    this.call = streamClient.newCall(builder.build());

    call.enqueue(this);
    updater.onReady();
  }

  @Override
  public void stop() {
    log.info("Stopping EventSource service.");

    if (call != null) {
      call.cancel();
    }
  }

  public void close() {
    stop();
    this.streamClient.connectionPool().evictAll();
    log.info("EventSource closed");
  }

  @Override // Callback
  public void onFailure(@NotNull Call call, @NotNull IOException e) {
    log.warn("SSE stream error", e);
    updater.onDisconnected();
  }

  @Override // Callback
  public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
    log.debug("SSE stream data: {}", response.message());

    try {
      if (!response.isSuccessful()) {
        throw new SSEStreamException("Invalid SSE HTTP response: " + response);
      }

      if (response.body() == null) {
        throw new SSEStreamException("Invalid SSE HTTP response: empty body");
      }

      updater.onConnected();

      final BufferedSource reader = response.body().source();

      String line;
      while ((line = reader.readUtf8Line()) != null) {
        log.info("SSE stream data: {}", line);

        if (line.startsWith("data:")) {
          Message msg = gson.fromJson(line.substring(6), Message.class);
          updater.update(msg);
        }
      }

      throw new SSEStreamException("End of SSE stream");
    } catch (Throwable ex) {
      log.warn("SSE Stream aborted: " + ex.getMessage());
      updater.onDisconnected();
      if (ex instanceof SSEStreamException) {
        throw ex;
      }
      throw new SSEStreamException(ex.getMessage(), ex);
    }
  }

  private static class SSEStreamException extends RuntimeException {
    public SSEStreamException(String msg, Throwable cause) {
      super(msg, cause);
    }

    public SSEStreamException(String msg) {
      super(msg);
    }
  }
}
