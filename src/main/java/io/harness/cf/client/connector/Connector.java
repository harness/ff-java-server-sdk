package io.harness.cf.client.connector;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Segment;
import java.util.List;
import lombok.NonNull;

public interface Connector {

  String authenticate() throws ConnectorException;

  List<FeatureConfig> getFlags() throws ConnectorException;

  FeatureConfig getFlag(@NonNull String identifier) throws ConnectorException;

  List<Segment> getSegments() throws ConnectorException;

  Segment getSegment(@NonNull String identifier) throws ConnectorException;

  void postMetrics(Metrics metrics) throws ConnectorException;

  void stream(Updater updater) throws ConnectorException;

  void close();
}
