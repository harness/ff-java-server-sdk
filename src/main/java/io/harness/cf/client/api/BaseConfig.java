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

  @Builder.Default private final boolean streamEnabled = true;
  @Builder.Default private final int pollIntervalInSeconds = 60;

  // configurations for Analytics
  @Builder.Default private final boolean analyticsEnabled = true;

  @Builder.Default private final boolean globalTargetEnabled = true;

  @Builder.Default
  @Getter(AccessLevel.NONE)
  private final int frequency = 60; // unit: second

  @Builder.Default private final int bufferSize = 1024;

  // Flag to set all attributes as private
  @Builder.Default private final boolean allAttributesPrivate = false;
  // Custom list to set the attributes which are private; move over to target
  @Builder.Default private final Set<String> privateAttributes = Collections.emptySet();

  @Builder.Default private final boolean debug = false;
  /** If metrics service POST call is taking > this time, we need to know about it */
  @Builder.Default private final long metricsServiceAcceptableDuration = 10000;

  public int getFrequency() {
    return Math.max(frequency, Config.MIN_FREQUENCY);
  }

  @Builder.Default private final Cache cache = new CaffeineCache(10000);

  private final Storage store;
}
