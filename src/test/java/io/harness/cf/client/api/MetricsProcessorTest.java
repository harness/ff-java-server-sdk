package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.collect.Maps;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Variation;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

@Slf4j
public class MetricsProcessorTest implements MetricsCallback {
  final int BUFFER_SIZE = 10; // 20000;
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
    Target target = Target.builder().identifier("harness").build();
    FeatureConfig feature = FeatureConfig.builder().feature("bool-flag").build();
    Variation variation = Variation.builder().identifier("true").value("true").build();

    for (int i = 1; i <= BUFFER_SIZE; i++) {
      WORKER_THREAD_POOL.submit(
          () -> {
            for (int j = 1; j <= BUFFER_SIZE; j++) {
              metricsProcessor.pushToQueue(target, feature.getFeature(), variation);

              metricsProcessor.flushQueue(); // mimic scheduled job
            }
          });
    }
    metricsProcessor.flushQueue();
    waitForAllMetricEventsToArrive(metricsProcessor, BUFFER_SIZE * BUFFER_SIZE);
    assertEquals(BUFFER_SIZE * BUFFER_SIZE, metricsProcessor.getMetricsSent());
  }

  @Test
  public void shouldNotThrowOutOfMemoryErrorWhenCreatingThreads() throws InterruptedException {
    final int TARGET_COUNT = 100;
    final int FLAG_COUNT = 500;
    final int VARIATION_COUNT = 4;

    long maxQueueMapSize = 0;
    long maxUniqueTargetSetSize = 0;

    for (int t = 0; t < TARGET_COUNT; t++) {
      Target target = Target.builder().identifier("harness" + t).build();
      for (int f = 0; f < FLAG_COUNT; f++) {
        FeatureConfig feature = FeatureConfig.builder().feature("bool-flag" + f).build();
        for (int v = 0; v < VARIATION_COUNT; v++) {
          Variation variation =
              Variation.builder().identifier("true" + v).name("name" + v).value("true").build();

          metricsProcessor.pushToQueue(target, feature.getFeature(), variation);

          maxQueueMapSize = Math.max(maxQueueMapSize, metricsProcessor.getQueueSize());
          maxUniqueTargetSetSize =
              Math.max(maxUniqueTargetSetSize, metricsProcessor.getTargetSetSize());
        }
      }

      if (t % 10 == 0) {
        log.info(
            "Metrics frequency map (cur: {} max: {}) Unique targets (cur: {} max: {}) Events sent ({}) Events pending ({})",
            metricsProcessor.getQueueSize(),
            maxQueueMapSize,
            metricsProcessor.getTargetSetSize(),
            maxUniqueTargetSetSize,
            metricsProcessor.getMetricsSent(),
            metricsProcessor.getPendingMetricsToBeSent());

        metricsProcessor.flushQueue(); // mimic scheduled job
      }
    }

    metricsProcessor.flushQueue();

    int waitingForCount = TARGET_COUNT * FLAG_COUNT * VARIATION_COUNT;
    waitForAllMetricEventsToArrive(metricsProcessor, waitingForCount);

    assertEquals(waitingForCount, metricsProcessor.getMetricsSent());
  }

  @SuppressWarnings("BusyWait")
  private void waitForAllMetricEventsToArrive(MetricsProcessor processor, int metricCount)
      throws InterruptedException {
    final int delayMs = 100;
    int maxWaitTime = 30_000 / delayMs;
    while (processor.getMetricsSent() < metricCount && maxWaitTime > 0) {
      System.out.println(
          "Waiting for all metric events to arrive..."
              + (metricCount - processor.getMetricsSent())
              + " events left");
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
