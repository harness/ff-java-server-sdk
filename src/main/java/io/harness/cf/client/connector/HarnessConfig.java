package io.harness.cf.client.connector;

import java.security.cert.X509Certificate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@ToString
public class HarnessConfig {
  public static final int MIN_FREQUENCY = 60;

  @Builder.Default private String configUrl = "https://config.ff.harness.io/api/1.0"; // Prod.

  @Builder.Default private String eventUrl = "https://events.ff.harness.io/api/1.0"; // Prod.

  /** timeout in milliseconds to connect to CF Server */
  @Builder.Default int connectionTimeout = 10000;

  /** timeout in milliseconds for reading data from CF Server */
  @Builder.Default int readTimeout = 30000;

  /** timeout in milliseconds for writing data to CF Server */
  @Builder.Default int writeTimeout = 10000;

  /** read timeout in minutes for SSE connections */
  @Builder.Default long sseReadTimeout = 1;

  /**
   * The timeout for flushing analytics on SDK close.
   * <p>
   * This option sets the maximum duration, in milliseconds, the SDK will wait for the
   * analytics data to be flushed after the SDK has been closed. If the flush process takes longer
   * than this timeout, the request will be canceled, and any remaining data will
   * not be sent. This ensures that the SDK does not hang indefinitely during shutdown.
   * <p>
   * The default value is {@code 30000ms} which is the default read timeout for requests made by the SDK
   * <p>
   * <b>Note:</b> This timeout only applies to the flush process that happens when
   * {@code flushAnalyticsOnClose} is set to {@code true}. It does not affect other
   * requests made by the SDK during normal operation.
   *
   * <p>Example usage:
   * <pre>
   * {@code
   * // Timeout the analytics flush request in 3000ms (3 seconds)
   * HarnessConfig harnessConfig =
   *                 HarnessConfig.builder().flushAnalyticsOnCloseTimeout(3000).build();
   *
   * // flush analytics on close is enabled via BaseConfig
   * BaseConfig config = BaseConfig.builder()
   *     .flushAnalyticsOnClose(true)
   *     .build();
   * }
   * </pre>
   */
  @Builder.Default private final int flushAnalyticsOnCloseTimeout = 30000;

  /**
   * list of trusted CAs - for when the given config/event URLs are signed with a private CA. You
   * should include intermediate CAs too to allow the HTTP client to build a full trust chain.
   */
  @Builder.Default List<X509Certificate> tlsTrustedCAs = null;

  /**
   * Defines the maximum number of retry attempts for certain types of requests:
   * authentication, polling, metrics, and reacting to stream events. If a request fails,
   * the SDK will retry up to this number of times before giving up.
   * <p>
   * - Authentication: Used for retrying authentication requests when the server is unreachable.
   * - Polling: Applies to requests that fetch feature flags and target groups periodically.
   * - Metrics: Applies to analytics requests for sending metrics data to the server.
   * - Reacting to Stream Events: Applies to requests triggered by streamed flag or group changes,
   *   where the SDK needs to fetch updated flag or group data.
   * <p>
   * Note: This setting does not apply to streaming requests (either the initial connection or
   * reconnecting after a disconnection). Streaming requests will always retry indefinitely
   * (infinite retries).
   */
  @Builder.Default private long maxRequestRetry = 10;
}
