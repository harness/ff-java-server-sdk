package io.harness.cf.client.connector;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Segment;
import java.util.List;
import lombok.NonNull;

public interface Connector {

  String authenticate() throws ConnectorException;

  /**
   * If the http interceptor detects unauthorised during an API call then this callback is used to
   * retry auth
   */
  void setOnUnauthorized(Runnable runnable);

  List<FeatureConfig> getFlags() throws ConnectorException;

  FeatureConfig getFlag(@NonNull String identifier) throws ConnectorException;

  List<Segment> getSegments() throws ConnectorException;

  Segment getSegment(@NonNull String identifier) throws ConnectorException;

  void postMetrics(Metrics metrics) throws ConnectorException;

  Service stream(Updater updater) throws ConnectorException;

  void close();

  boolean getShouldFlushAnalyticsOnClose();

  void setIsShuttingDown();
}
