package io.harness.cf.client.api.analytics;

import static junit.framework.TestCase.*;

import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Analytics;
import io.harness.cf.client.dto.Target;
import io.harness.cf.metrics.model.Metrics;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class AnalyticsPublisherServiceTest {

  AnalyticsPublisherService publisherService;

  @Before
  public void setUp() {
    publisherService =
        new AnalyticsPublisherService(
            null,
            Config.builder().build(),
            null,
            null,
            AnalyticsCacheFactory.create(AnalyticsCacheFactory.GUAVA_CACHE));
  }

  @Test
  public void validateSummaryData() {

    Metrics metrics = publisherService.prepareSummaryMetricsBody(getAnalyticsData(10, 20));

    assertNotNull(metrics.getTargetData());
    assertTrue(metrics.getTargetData().size() >= 20);

    assertNotNull(metrics.getMetricsData());
    assertEquals(10, metrics.getMetricsData().size());

    metrics = publisherService.prepareSummaryMetricsBody(getAnalyticsData(10, 20));
    assertNull(metrics.getTargetData());

    assertNotNull(metrics.getMetricsData());
    assertEquals(10, metrics.getMetricsData().size());
  }

  private Map<Analytics, Integer> getAnalyticsData(int featureLength, int targetLength) {
    Map<Analytics, Integer> analyticsData = new HashMap<>();
    for (int i = 0; i < featureLength; i++) {
      for (int j = 0; j < targetLength; j++) {
        analyticsData.put(
            Analytics.builder()
                .featureConfig(new FeatureConfig().feature("TestFF" + i))
                .variation(
                    new Variation()
                        .identifier("TestVariation" + i)
                        .name("TestVariation" + i)
                        .value("TestVariationValue" + i))
                .target(
                    Target.builder()
                        .identifier("Target" + j)
                        .name("Target" + j)
                        .attribute("testAttributeKey", "testAttributeValue")
                        .build())
                .build(),
            1);
      }
    }

    return analyticsData;
  };
}
