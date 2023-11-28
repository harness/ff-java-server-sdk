package io.harness.cf.client.connector;

import static io.harness.cf.client.common.Utils.redactHeaders;

import com.google.gson.Gson;
import io.harness.cf.client.common.SdkCodes;
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

    httpClientBuilder.addInterceptor(new NewRetryInterceptor(retryBackoffDelay));
    return httpClientBuilder.build();
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
    log.info("EventSource connecting with url {} and headers {}", url, redactHeaders(headers));

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
    if (this.streamClient != null) {
      this.streamClient.connectionPool().evictAll();
    }
    log.info("EventSource closed");
  }

  @Override // Callback
  public void onFailure(@NotNull Call call, @NotNull IOException e) {
    log.warn("SSE stream error", e);
    updater.onDisconnected(e.getMessage());
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
        log.debug("SSE stream data: {}", line);

        if (line.startsWith("data:")) {
          Message msg = gson.fromJson(line.substring(6), Message.class);
          SdkCodes.infoStreamEventReceived(line.substring(6));
          updater.update(msg);
        }
      }
      log.warn("End of SSE stream");
      updater.onDisconnected("End of SSE stream");
    } catch (Throwable ex) {
      log.warn("SSE Stream aborted: " + ex.getMessage());
      log.trace("SSE Stream aborted trace", ex);
      updater.onDisconnected(ex.getMessage());
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
