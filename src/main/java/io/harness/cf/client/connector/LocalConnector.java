package io.harness.cf.client.connector;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.NonNull;

public class LocalConnector implements Connector {
  @Override
  public Optional<String> authenticate(Consumer<String> onError) {
    return Optional.empty();
  }

  @Override
  public List<FeatureConfig> getFlags() {
    return null;
  }

  @Override
  public Optional<FeatureConfig> getFlag(@NonNull String identifier) {
    return Optional.empty();
  }

  @Override
  public List<Segment> getSegments() {
    return null;
  }

  @Override
  public Optional<Segment> getSegment(@NonNull String identifier) {
    return Optional.empty();
  }

  @Override
  public void postMetrics(Metrics metrics) {}

  @Override
  public void stream(Updater updater) {}

  @Override
  public void close() {}
}
