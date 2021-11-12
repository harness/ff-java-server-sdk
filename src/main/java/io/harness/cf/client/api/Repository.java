package io.harness.cf.client.api;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import lombok.NonNull;

public interface Repository extends Query {

  // put values
  void setFlag(@NonNull String identifier, @NonNull FeatureConfig featureConfig);

  void setSegment(@NonNull String identifier, @NonNull Segment segment);

  // remove values
  void deleteFlag(@NonNull String identifier);

  void deleteSegment(@NonNull String identifier);

  void close();
}
