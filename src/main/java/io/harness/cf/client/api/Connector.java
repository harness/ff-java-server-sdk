package io.harness.cf.client.api;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.NonNull;
import okhttp3.Request;
import org.apache.commons.lang3.tuple.ImmutablePair;

public interface Connector {

  Optional<ImmutablePair<String, String>> authenticate(Consumer<String> onError);

  List<FeatureConfig> getFlags();

  Optional<FeatureConfig> getFlag(@NonNull String identifier);

  List<Segment> getSegments();

  Optional<Segment> getSegment(@NonNull String identifier);

  void postMetrics(Metrics metrics);

  Request stream();
}
