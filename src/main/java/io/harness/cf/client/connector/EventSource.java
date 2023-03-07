package io.harness.cf.client.connector;

import com.google.gson.Gson;
import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.logger.LogUtil;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;

@Slf4j
public class EventSource implements ServerSentEvent.Listener, AutoCloseable, Service {

  private final OkSse okSse;
  private final Updater updater;
  private final Gson gson = new Gson();
  private final Request.Builder builder;
  private int retryTime;
  private HttpLoggingInterceptor loggingInterceptor;

  private ServerSentEvent sse;

  static {
    LogUtil.setSystemProps();
  }

  public EventSource(
      @NonNull String url,
      Map<String, String> headers,
      @NonNull Updater updater,
      long sseReadTimeoutMins) {
    this(url, headers, updater, sseReadTimeoutMins, 2_000, null);
  }

  EventSource(
      @NonNull String url,
      Map<String, String> headers,
      @NonNull Updater updater,
      long sseReadTimeoutMins,
      int retryDelayMs,
      List<X509Certificate> trustedCAs) {
    this.updater = updater;
    this.retryTime = retryDelayMs;
    okSse = new OkSse(makeStreamClient(sseReadTimeoutMins, trustedCAs));
    builder = new Request.Builder().url(url);
    headers.put("User-Agent", "JavaSDK " + io.harness.cf.Version.VERSION);
    headers.forEach(builder::header);
    updater.onReady();
    log.info("EventSource initialized with url {} and headers {}", url, headers);
  }

  protected OkHttpClient makeStreamClient(
      long sseReadTimeoutMins, List<X509Certificate> trustedCAs) {
    OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder()
            .readTimeout(sseReadTimeoutMins, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true);

    setupTls(httpClientBuilder, trustedCAs);

    if (log.isDebugEnabled()) {
      loggingInterceptor = new HttpLoggingInterceptor();
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
      httpClientBuilder.addInterceptor(loggingInterceptor);
    } else {
      httpClientBuilder.interceptors().remove(loggingInterceptor);
      loggingInterceptor = null;
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
    log.info("stream http client definition complete");
    return httpClientBuilder.build();
  }

  @SneakyThrows
  private void setupTls(OkHttpClient.Builder httpClientBuilder, List<X509Certificate> trustedCAs) {

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
    } catch (GeneralSecurityException ex) {
      log.warn("Failed to setup TLS on SSE endpoint: " + ex.getMessage(), ex);
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void onOpen(ServerSentEvent serverSentEvent, Response response) {
    log.info("EventSource onOpen");
    if (updater != null) {
      log.info("EventSource connected!");
      updater.onConnected();
    }
  }

  @Override
  public void onMessage(ServerSentEvent sse, String id, String event, String message) {
    log.info("EventSource onMessage {}", message);
    Message msg = gson.fromJson(message, Message.class);
    updater.update(msg);
  }

  @Override
  public void onComment(ServerSentEvent serverSentEvent, String s) {
    /* comment is not used */
  }

  @Override
  public boolean onRetryTime(ServerSentEvent serverSentEvent, long l) {
    log.warn("EventSource onRetryTime {}", l);
    return true;
  }

  @Override
  public boolean onRetryError(
      ServerSentEvent serverSentEvent, Throwable throwable, Response response) {

    log.warn(
        "EventSource onRetryError [throwable={} message={}]",
        throwable.getClass().getSimpleName(),
        throwable.getMessage());
    log.trace("onRetryError exception", throwable);

    updater.onError();
    if (response != null) {
      return shouldRetryForHttpErrorCode(response.code());
    }
    return true;
  }

  private boolean shouldRetryForHttpErrorCode(int httpCode) {
    if (httpCode == 501) return false;

    return httpCode == 429 || httpCode >= 500;
  }

  @Override
  public void onClosed(ServerSentEvent serverSentEvent) {
    log.info("EventSource onClosed - disconnected");
    updater.onDisconnected();
  }

  @SneakyThrows
  @Override
  public Request onPreRetry(ServerSentEvent serverSentEvent, Request request) {
    log.info("EventSource onPreRetry, retry after {}ms", retryTime);
    Thread.sleep(retryTime);
    retryTime = retryTime * 2;
    log.info("EventSource retrying");
    return request;
  }

  @Override
  public void start() {
    log.info("Starting EventSource service.");
    sse = okSse.newServerSentEvent(builder.build(), this);
  }

  @Override
  public void stop() {
    log.info("Stopping EventSource service.");
    sse.close();
  }

  public void close() {
    stop();
    okSse.getClient().connectionPool().evictAll();
    log.info("EventSource closed");
  }
}
