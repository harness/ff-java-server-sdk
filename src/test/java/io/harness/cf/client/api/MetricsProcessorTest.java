package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import com.google.common.collect.Maps;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
  private void waitForAllMetricEventsToArrive(MetricsProcessor processor, int totalMetricCount)
      throws InterruptedException {
    final int delayMs = 100;
    int maxWaitTime = 30_000 / delayMs;
    while (processor.getMetricsSent() < totalMetricCount && maxWaitTime > 0) {

      System.out.printf(
          "Waiting for all metric events to arrive... totalMetricCount=%d metricsSent=%d mapSize=%d pending=%d\n",
          totalMetricCount,
          processor.getMetricsSent(),
          processor.getQueueSize(),
          processor.getPendingMetricsToBeSent());

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
  void testPrepareSummaryMetricsBody() {
    Target target = Target.builder().identifier("harness").build();
    FeatureConfig feature = FeatureConfig.builder().feature("bool-flag").build();
    Variation variation = Variation.builder().identifier("true").value("true").build();
    MetricEvent event = new MetricEvent(feature.getFeature(), target, variation);

    Set<Target> uniqueTargets = new HashSet<>();
    uniqueTargets.add(target);

    Map<MetricEvent, Long> freqMap = Maps.newHashMap();
    freqMap.put(event, 6L);

    Metrics metrics = metricsProcessor.prepareSummaryMetricsBody(freqMap, uniqueTargets);

    assertNotNull(metrics);
    assertNotNull(metrics.getTargetData());
    assertNotNull(metrics.getMetricsData());

    assertEquals(target.getIdentifier(), metrics.getTargetData().get(0).getIdentifier());
    assertEquals(6, metrics.getMetricsData().get(0).getCount());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPostCorrectMetrics_WhenGlobalTargetEnabledOrDisabled(boolean globalTargetEnabled)
      throws ConnectorException {
    final Connector mockConnector = Mockito.mock(Connector.class);
    final BaseConfig mockConfig = Mockito.mock(BaseConfig.class);
    final ArgumentCaptor<Metrics> metricsArgumentCaptor = ArgumentCaptor.forClass(Metrics.class);

    when(mockConfig.isGlobalTargetEnabled()).thenReturn(globalTargetEnabled);
    when(mockConfig.getBufferSize()).thenReturn(10);
    doNothing().when(mockConnector).postMetrics(metricsArgumentCaptor.capture());

    final MetricsProcessor processor =
        new MetricsProcessor(mockConnector, mockConfig, Mockito.mock(MetricsCallback.class));

    final Target target = Target.builder().identifier("target123").build();
    final Variation variation = Variation.builder().identifier("true").value("true").build();
    processor.pushToQueue(target, "feature1", variation);
    processor.pushToQueue(target, "feature1", variation);
    processor.pushToQueue(target, "feature2", variation);
    processor.pushToQueue(target, "feature2", variation);
    processor.pushToQueue(target, "feature3", variation);
    processor.pushToQueue(target, "feature3", variation);
    processor.runOneIteration(); // Mimic scheduled job

    final Metrics sentMetrics = metricsArgumentCaptor.getValue();

    assertNotNull(sentMetrics.getMetricsData());
    assertNotNull(sentMetrics.getTargetData());

    assertEquals(1, sentMetrics.getTargetData().size());
    assertEquals("target123", sentMetrics.getTargetData().get(0).getIdentifier());

    assertEquals(3, sentMetrics.getMetricsData().size());
    for (MetricsData md : sentMetrics.getMetricsData()) {
      final Map<String, String> map = keyValueArrayToMap(md.getAttributes());
      assertEquals(2, md.getCount());
      if (globalTargetEnabled) {
        assertEquals("__global__cf_target", map.get("target"));
      } else {
        assertEquals("target123", map.get("target"));
      }
    }
  }

  private Map<String, String> keyValueArrayToMap(List<KeyValue> keyValueList) {
    final Map<String, String> map = new HashMap<>();
    for (KeyValue kv : keyValueList) {
      map.put(kv.getKey(), kv.getValue());
    }
    return map;
  }
}
