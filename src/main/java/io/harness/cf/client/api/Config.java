package io.harness.cf.client.api;

import io.harness.cf.client.common.Cache;
import io.harness.cf.client.common.Storage;
import java.util.Collections;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AllArgsConstructor
public class Config {

  public static final int MIN_FREQUENCY = 60;

  @Builder.Default private boolean streamEnabled = true;
  @Builder.Default private int pollIntervalInSeconds = 60;

  // configurations for Analytics
  @Builder.Default private boolean analyticsEnabled = true;

  @Builder.Default
  @Getter(AccessLevel.NONE)
  private int frequency = 60; // unit: second

  @Builder.Default private int bufferSize = 1024;

  // Flag to set all attributes as private
  @Builder.Default private boolean allAttributesPrivate = false;
  // Custom list to set the attributes which are private; move over to target
  @Builder.Default private Set<String> privateAttributes = Collections.emptySet();

  @Getter @Builder.Default boolean debug = false;
  /** If metrics service POST call is taking > this time, we need to know about it */
  @Getter @Builder.Default long metricsServiceAcceptableDuration = 10000;

  public int getFrequency() {
    return Math.max(frequency, Config.MIN_FREQUENCY);
  }

  @Getter @Builder.Default Cache cache = new CaffeineCache(10000);

  @Getter Storage store;
}
