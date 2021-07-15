package io.harness.cf.client.api.mock;

import io.harness.cf.client.api.Config;
import java.util.Set;

public class MockedCfConfiguration extends Config {

  public static final int MOCKED_MIN_FREQUENCY;

  static {
    MOCKED_MIN_FREQUENCY = 2;
  }

  public MockedCfConfiguration(
      String configUrl,
      String eventUrl,
      boolean streamEnabled,
      int pollIntervalInSeconds,
      boolean analyticsEnabled,
      int frequency,
      int bufferSize,
      String analyticsCacheType,
      boolean allAttributesPrivate,
      Set<String> privateAttributes,
      int connectionTimeout,
      int readTimeout,
      int writeTimeout,
      boolean debug,
      long metricsServiceAcceptableDuration) {

    super(
        configUrl,
        eventUrl,
        streamEnabled,
        pollIntervalInSeconds,
        analyticsEnabled,
        frequency,
        bufferSize,
        analyticsCacheType,
        allAttributesPrivate,
        privateAttributes,
        connectionTimeout,
        readTimeout,
        writeTimeout,
        debug,
        metricsServiceAcceptableDuration);
  }

  @Override
  public int getFrequency() {

    return MOCKED_MIN_FREQUENCY;
  }
}
