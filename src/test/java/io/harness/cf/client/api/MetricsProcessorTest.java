package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Maps;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Variation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
public class MetricsProcessorTest implements MetricsCallback {
  final int BUFFER_SIZE = 10;
  @Mock private Connector connector;

  private MetricsProcessor metricsProcessor;

  @BeforeEach
  public void setup() {
    MockitoAnnotations.openMocks(this);
    metricsProcessor =
        Mockito.spy(
            new MetricsProcessor(
                connector, BaseConfig.builder().bufferSize(BUFFER_SIZE).build(), this));

    metricsProcessor.reset();
  }

  @Test
  public void testPushToQueue() throws InterruptedException {
    ExecutorService WORKER_THREAD_POOL = Executors.newFixedThreadPool(BUFFER_SIZE);
    CountDownLatch latch = new CountDownLatch(BUFFER_SIZE);
    Target target = Target.builder().identifier("harness").build();
    FeatureConfig feature = FeatureConfig.builder().feature("bool-flag").build();
    Variation variation = Variation.builder().identifier("true").value("true").build();

    for (int i = 1; i <= BUFFER_SIZE; i++) {
      WORKER_THREAD_POOL.submit(
          () -> {
            for (int j = 1; j <= BUFFER_SIZE; j++) {
              metricsProcessor.pushToQueue(target, feature.getFeature(), variation);
            }
            latch.countDown();
          });
    }
    latch.await();

    verify(metricsProcessor, times(BUFFER_SIZE - 1)).runOneIteration();
  }

  @Test
  public void shouldNotThrowOutOfMemoryErrorWhenCreatingThreads()
      throws InterruptedException, ConnectorException {
    final int METRIC_COUNT = 1_000_000;

    Target target = Target.builder().identifier("harness").build();
    FeatureConfig feature = FeatureConfig.builder().feature("bool-flag").build();
    Variation variation = Variation.builder().identifier("true").value("true").build();

    Target target2 = Target.builder().identifier("harness2").build();
    FeatureConfig feature2 = FeatureConfig.builder().feature("bool-flag2").build();
    Variation variation2 = Variation.builder().identifier("true").value("true").build();

    for (int j = 0; j < METRIC_COUNT; j++) {
      metricsProcessor.pushToQueue(target, feature.getFeature(), variation);
      metricsProcessor.pushToQueue(target2, feature2.getFeature(), variation2);
    }

    metricsProcessor.flushQueue();

    waitForAllMetricEventsToArrive(metricsProcessor, METRIC_COUNT * 2);

    assertEquals(METRIC_COUNT * 2, metricsProcessor.getMetricsSent());
  }

  private void waitForAllMetricEventsToArrive(MetricsProcessor processor, int metricCount)
      throws InterruptedException {
    final int delayMs = 100;
    int maxWaitTime = 30_000 / delayMs;
    while (processor.getMetricsSent() < metricCount && maxWaitTime > 0) {
      System.out.println("Waiting for all metric events to arrive...");
      Thread.sleep(delayMs);
      maxWaitTime--;
    }

    if (maxWaitTime == 0) {
      fail("Timed out");
    }
  }

  @Override
  public void onMetricsReady() {
    // not used
  }

  @Override
  public void onMetricsError(@NonNull String error) {
    // not used
  }

  @Override
  public void onMetricsFailure() {
    // not used
  }

  @Test
  public void testPrepareSummaryMetricsBody() {
    Target target = Target.builder().identifier("harness").build();
    FeatureConfig feature = FeatureConfig.builder().feature("bool-flag").build();
    Variation variation = Variation.builder().identifier("true").value("true").build();
    MetricEvent event = new MetricEvent(feature.getFeature(), target, variation);

    Set<Target> uniqueTargets = new HashSet<>();
    uniqueTargets.add(target);

    Map<MetricEvent, Long> map = Maps.newHashMap();
    map.put(event, 6L);

    Metrics metrics = metricsProcessor.prepareSummaryMetricsBody(map, uniqueTargets);

    assert metrics.getTargetData() != null;
    assert metrics.getTargetData().get(1).getIdentifier().equals(target.getIdentifier());
  }
}
