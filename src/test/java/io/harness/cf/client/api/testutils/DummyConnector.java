package io.harness.cf.client.api.testutils;

import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.connector.Service;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.MetricsData;
import io.harness.cf.model.Segment;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import lombok.NonNull;

public class DummyConnector implements Connector {

  private final LongAdder totalEvaluations = new LongAdder();
  private final boolean dumpPostedMetrics;

  public DummyConnector() {
    this.dumpPostedMetrics = false;
  }

  public DummyConnector(boolean dumpPostedMetrics) {
    this.dumpPostedMetrics = dumpPostedMetrics;
  }

  @Override
  public String authenticate() throws ConnectorException {
    return "dummy";
  }

  @Override
  public void setOnUnauthorized(Runnable runnable) {}

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {
    return Collections.emptyList();
  }

  @Override
  public FeatureConfig getFlag(@NonNull String identifier) throws ConnectorException {
    return null;
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    return Collections.emptyList();
  }

  @Override
  public Segment getSegment(@NonNull String identifier) throws ConnectorException {
    return null;
  }

  @Override
  public void postMetrics(Metrics metrics) throws ConnectorException {
    System.out.println("postMetrics called");
    if (metrics.getMetricsData() != null) {
      totalEvaluations.add(metrics.getMetricsData().stream().mapToInt(MetricsData::getCount).sum());
      int approxPayloadSize =
          metrics.getMetricsData().stream().mapToInt(d -> d.toString().length()).sum();
      System.out.println("approx. Payload Size " + approxPayloadSize);
      if (dumpPostedMetrics) {
        metrics.getMetricsData().forEach(md -> System.out.println(md.toString()));
      }
    }
  }

  @Override
  public Service stream(Updater updater) throws ConnectorException {
    return null;
  }

  public int getTotalMetricEvaluations() {
    return (int) totalEvaluations.sum();
  }

  @Override
  public void close() {}

  @Override
  public void setIsShuttingDown() {}
}
