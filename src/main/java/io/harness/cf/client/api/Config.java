package io.harness.cf.client.api;

import io.harness.cf.client.api.analytics.AnalyticsCacheFactory;
import java.util.Collections;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Config {
  public static final int MIN_FREQUENCY = 60;

  @Builder.Default private String configUrl = "https://config.ff.harness.io/api/1.0"; // UAT

  @Builder.Default private String eventUrl = "https://events.ff.harness.io/api/1.0"; // UAT

  @Builder.Default private boolean streamEnabled = true;
  @Builder.Default private int pollIntervalInSeconds = 60;

  // configurations for Analytics
  @Builder.Default private boolean analyticsEnabled = true;

  @Builder.Default
  @Getter(AccessLevel.NONE)
  private int frequency = 60; // unit: second

  @Builder.Default
  @Getter(AccessLevel.NONE)
  private int bufferSize = 1024;

  @Builder.Default private String analyticsCacheType = AnalyticsCacheFactory.GUAVA_CACHE;

  // Flag to set all attributes as private
  @Builder.Default private boolean allAttributesPrivate = false;
  // Custom list to set the attributes which are private; move over to target
  @Builder.Default private Set<String> privateAttributes = Collections.emptySet();

  /** timeout in milliseconds to connect to CF Server */
  @Builder.Default int connectionTimeout = 10000;
  /** timeout in milliseconds for reading data from CF Server */
  @Builder.Default int readTimeout = 30000;
  /** timeout in milliseconds for writing data to CF Server */
  @Builder.Default int writeTimeout = 10000;

  @Getter @Builder.Default boolean debug = false;
  /** If metrics service POST call is taking > this time, we need to know about it */
  @Getter @Builder.Default long metricsServiceAcceptableDuration = 10000;

  public int getFrequency() {
    return Math.max(frequency, Config.MIN_FREQUENCY);
  }

  /*
   BufferSize must be a power of 2 for LMAX to work. This function vaidates
   that. Source: https://stackoverflow.com/a/600306/1493480
  */
  public int getBufferSize() throws CfClientException {
    if (!(bufferSize != 0 && ((bufferSize & (bufferSize - 1)) == 0))) {
      throw new CfClientException("BufferSize must be a power of 2");
    }
    return bufferSize;
  }
}
