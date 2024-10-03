package io.harness.cf.client.api;

import io.harness.cf.client.common.Cache;
import io.harness.cf.client.common.Storage;
import java.util.Collections;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class BaseConfig {
  public static final int MIN_FREQUENCY = 60;
  public static final long DEFAULT_REQUEST_RETRIES = 10;

  @Builder.Default private final boolean streamEnabled = true;
  @Builder.Default private final int pollIntervalInSeconds = 60;

  // configurations for Analytics
  @Builder.Default private final boolean analyticsEnabled = true;

  @Builder.Default private final boolean globalTargetEnabled = true;

  /** If you do not need to be warned about every variation that returned a default value, set this to true */
  @Builder.Default private final boolean sdkCode6001Suppressed = false;

  @Builder.Default
  @Getter(AccessLevel.NONE)
  private final int frequency = 60; // unit: second

  @Builder.Default private final int bufferSize = 5000;

  // Flag to set all attributes as private
  @Deprecated @Builder.Default private final boolean allAttributesPrivate = false;
  // Custom list to set the attributes which are private; move over to target
  @Deprecated @Builder.Default private final Set<String> privateAttributes = Collections.emptySet();

  @Builder.Default private final boolean debug = false;

  /** If metrics service POST call is taking > this time, we need to know about it */
  @Builder.Default private final long metricsServiceAcceptableDuration = 10000;

  /** store previous and current version of the FeatureConfig */
  @Builder.Default private final boolean enableFeatureSnapshot = false;

  /** Get metrics post frequency in seconds */
  public int getFrequency() {
    return Math.max(frequency, Config.MIN_FREQUENCY);
  }

  @Builder.Default private final Cache cache = new CaffeineCache(10000);

  private final Storage store;

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
   * <p>
   * The default value is {@code 10}.
   * <p>
   * <b>Note:</b> This setting does not apply to streaming requests (either the initial connection or
   * reconnecting after a disconnection). Streaming requests will always retry indefinitely
   * (infinite retries).
   * <p>
   * Example usage:
   * <pre>
   * {@code
   * BaseConfig config = BaseConfig.builder()
   *     .maxRequestRetry(20)
   *     .build();
   * }
   * </pre>
   */
  @Builder.Default private final long maxRequestRetry = DEFAULT_REQUEST_RETRIES;

  /**
   * Indicates whether to flush analytics data when the SDK is closed.
   * <p>
   * When set to {@code true}, any remaining analytics data (such as metrics)
   * will be sent to the server before the SDK is fully closed. If {@code false},
   * the data will not be flushed, and any unsent analytics data may be lost.
   * <p>
   * The default value is {@code false}.
   * <p>
   * <b>Note:</b> The flush will attempt to send the data in a single request.
   * Any failures during this process will not be retried, and the analytics data
   * may be lost.
   *
   * <p>Example usage:
   * <pre>
   * {@code
   * BaseConfig config = BaseConfig.builder()
   *     .flushAnalyticsOnClose(true)
   *     .build();
   * }
   * </pre>
   */
  @Builder.Default private final boolean flushAnalyticsOnClose = false;
}
