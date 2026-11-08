package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Variation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetricsLifecycleRegressionTest {
  private static Object field(Object owner, String name) throws Exception {
    Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner instanceof Class ? null : owner);
  }

  private static MetricsProcessor metrics(Connector connector) {
    return new MetricsProcessor(
        connector,
        BaseConfig.builder().analyticsEnabled(true).build(),
        mock(MetricsCallback.class),
        false);
  }

  private static void evaluate(MetricsProcessor processor, String id) {
    processor.registerEvaluation(
        Target.builder().identifier(id).name("Synthetic").build(),
        "flag",
        Variation.builder().identifier("true").value("true").build());
  }

  @Test
  void analyticsHistoryMustReleaseTargetsWhenClientCloses() throws Exception {
    MetricsProcessor processor = metrics(mock(Connector.class, withSettings().stubOnly()));
    processor.reset();
    try {
      for (int batch = 0; batch < 3; batch++) {
        for (int i = 0; i < 40000; i++) evaluate(processor, "synthetic-" + batch + "-" + i);
        processor.runOneIteration();
        assertEquals(0, processor.getTargetSetSize());
        assertEquals(
            Math.min(100000, (batch + 1) * 40000),
            ((Set<?>) field(processor, "globalTargetSet")).size());
      }
      processor.close();
      int retained = ((Set<?>) field(processor, "globalTargetSet")).size();
      assertEquals(0, retained, "Analytics history retained targets after close: " + retained);
    } finally {
      processor.close();
      processor.reset();
    }
  }

  @Test
  void analyticsDeduplicationMustBeScopedToEachClient() throws Exception {
    Connector firstConnector = mock(Connector.class);
    Connector secondConnector = mock(Connector.class);
    MetricsProcessor first = metrics(firstConnector);
    MetricsProcessor second = metrics(secondConnector);
    first.reset();
    try {
      evaluate(first, "shared-synthetic-target");
      first.runOneIteration();
      first.close();
      evaluate(second, "shared-synthetic-target");
      second.runOneIteration();
      ArgumentCaptor<Metrics> sent = ArgumentCaptor.forClass(Metrics.class);
      verify(secondConnector).postMetrics(sent.capture());
      assertEquals(
          1, sent.getValue().getTargetData().size(), "Client B must send its own target data");
    } finally {
      first.close();
      second.close();
      first.reset();
    }
  }

  @Test
  void failedAnalyticsPostMustNotMarkTargetsAsDelivered() throws Exception {
    Connector connector = mock(Connector.class);
    doThrow(new ConnectorException("synthetic 503")).doNothing().when(connector).postMetrics(any());
    MetricsProcessor processor = metrics(connector);
    processor.reset();
    try {
      evaluate(processor, "retry-target");
      processor.runOneIteration();
      evaluate(processor, "retry-target");
      processor.runOneIteration();
      ArgumentCaptor<Metrics> sent = ArgumentCaptor.forClass(Metrics.class);
      verify(connector, times(2)).postMetrics(sent.capture());
      assertEquals(
          1,
          sent.getAllValues().get(1).getTargetData().size(),
          "Target data must be sent after the first POST failed");
    } finally {
      processor.close();
      processor.reset();
    }
  }

  @Test
  void failedBatchIsRetriedWithoutAnotherEvaluation() throws Exception {
    Connector connector = mock(Connector.class);
    doThrow(new ConnectorException("synthetic 503")).doNothing().when(connector).postMetrics(any());
    MetricsProcessor processor = metrics(connector);
    try {
      evaluate(processor, "retry-without-traffic");
      processor.runOneIteration();
      assertEquals(1, processor.getPendingMetricsToBeSent());
      processor.runOneIteration();
      ArgumentCaptor<Metrics> payload = ArgumentCaptor.forClass(Metrics.class);
      verify(connector, times(2)).postMetrics(payload.capture());
      assertEquals(
          "retry-without-traffic",
          payload.getAllValues().get(1).getTargetData().get(0).getIdentifier());
      assertEquals(1, processor.getMetricsSent());
      assertEquals(0, processor.getPendingMetricsToBeSent());
    } finally {
      processor.close();
    }
  }

  @Test
  void registrationDuringUploadKeepsMetadataForTheNextBatch() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch uploading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              uploading.countDown();
              assertTrue(release.await(3, TimeUnit.SECONDS));
              return null;
            })
        .when(connector)
        .postMetrics(any());
    MetricsProcessor processor = metrics(connector);
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      evaluate(processor, "before-upload");
      Future<?> first = worker.submit(processor::runOneIteration);
      assertTrue(uploading.await(3, TimeUnit.SECONDS));
      evaluate(processor, "during-upload");
      release.countDown();
      first.get(3, TimeUnit.SECONDS);
      processor.runOneIteration();
      ArgumentCaptor<Metrics> payload = ArgumentCaptor.forClass(Metrics.class);
      verify(connector, times(2)).postMetrics(payload.capture());
      assertEquals(
          "during-upload", payload.getAllValues().get(1).getTargetData().get(0).getIdentifier());
      assertEquals(2, processor.getMetricsSent());
    } finally {
      release.countDown();
      worker.shutdownNow();
      processor.close();
    }
  }

  @Test
  void historyEvictionAllowsMetadataToBeSentAgain() throws Exception {
    Connector connector = mock(Connector.class, withSettings().stubOnly());
    AtomicInteger targetCount = new AtomicInteger();
    doAnswer(
            invocation -> {
              targetCount.set(((Metrics) invocation.getArgument(0)).getTargetData().size());
              return null;
            })
        .when(connector)
        .postMetrics(any());
    MetricsProcessor processor = metrics(connector);
    try {
      evaluate(processor, "oldest");
      processor.runOneIteration();
      for (int i = 0; i < 100000; i++) evaluate(processor, "new-" + i);
      processor.runOneIteration();
      evaluate(processor, "oldest");
      processor.runOneIteration();
      assertEquals(1, targetCount.get());
      processor.close();
      processor.close();
      evaluate(processor, "after-close");
      assertEquals(0, processor.getTargetSetSize());
      assertEquals(0, processor.getQueueSize());
    } finally {
      processor.close();
    }
  }
}
