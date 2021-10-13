package io.harness.cf.client.api;

import io.harness.cf.client.api.mock.MockedAnalyticsHandlerCallback;
import io.harness.cf.client.api.mock.MockedCfClient;
import io.harness.cf.client.api.mock.MockedCfConfiguration;
import io.harness.cf.client.api.mock.MockedFeatureRepository;
import io.harness.cf.client.dto.Target;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class CfClientMetricsTest {

  @Test
  public void testMetrics() {

    final String mock = "Mock";
    final String apiKey = String.valueOf(System.currentTimeMillis());
    final MockedCfConfiguration cfConfiguration = new MockedCfConfiguration();
    final MockedCfClient cfClient = new MockedCfClient();

    cfClient.initialize(apiKey, cfConfiguration);

    final Target target =
        Target.builder().identifier(mock).isPrivate(false).attribute(mock, mock).name(mock).build();

    while (!cfClient.isInitialized()) {

      Thread.yield();
    }

    final AtomicInteger timerEventsCount = new AtomicInteger();
    final AtomicInteger metricsEventsCount = new AtomicInteger();

    final MockedAnalyticsHandlerCallback metricsCallback =
        new MockedAnalyticsHandlerCallback() {

          @Override
          public void onTimer() {

            timerEventsCount.incrementAndGet();
          }

          @Override
          public void onMetrics() {

            metricsEventsCount.incrementAndGet();
          }
        };

    try {

      cfClient.addCallback(metricsCallback);
    } catch (IllegalStateException e) {

      Assert.fail(e.getMessage());
    }

    final int evaluationsCount = 10;

    for (int x = 0; x < evaluationsCount; x++) {

      cfClient.boolVariation(MockedFeatureRepository.MOCK_BOOL, target, false);
    }

    while (metricsEventsCount.get() < evaluationsCount) {

      Thread.yield();
    }

    Assert.assertEquals(evaluationsCount, metricsEventsCount.get());

    try {

      Thread.sleep((MockedCfConfiguration.MOCKED_MIN_FREQUENCY + 1) * 1000L);
    } catch (InterruptedException e) {

      Assert.fail(e.getMessage());
    }

    Assert.assertTrue(timerEventsCount.get() > 0);

    try {

      cfClient.removeCallback(metricsCallback);
    } catch (IllegalStateException e) {

      Assert.fail(e.getMessage());
    }

    cfClient.destroy();
    Assert.assertFalse(cfClient.isInitialized);
  }
}
