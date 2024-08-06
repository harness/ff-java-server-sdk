package io.harness.cf.client.api;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

public interface Query {

  Optional<FeatureConfig> getFlag(@NonNull String identifier);

  Optional<Segment> getSegment(@NonNull String identifier);

  List<String> findFlagsBySegment(@NonNull String identifier);

  Optional<FeatureConfig[]> getCurrentAndPreviousFeatureConfig(@NonNull String identifier);

  List<String> getAllFeatureIdentifiers(String prefix);
}
