package io.harness.cf.client.api.mock;

import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.analytics.AnalyticsCacheFactory;
import java.util.Collections;

public class MockedCfConfiguration extends Config {

  public static final int MOCKED_MIN_FREQUENCY;

  static {
    MOCKED_MIN_FREQUENCY = 2;
  }

  public MockedCfConfiguration() {

    super(
        "",
        "",
        true,
        10,
        true,
        2,
        1024,
        AnalyticsCacheFactory.GUAVA_CACHE,
        false,
        Collections.emptySet(),
        10000,
        30000,
        10000,
        true,
        10000,
        30);
  }

  @Override
  public int getFrequency() {

    return MOCKED_MIN_FREQUENCY;
  }
}
