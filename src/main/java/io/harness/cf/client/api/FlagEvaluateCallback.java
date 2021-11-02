package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import lombok.NonNull;

interface FlagEvaluateCallback {
  void processEvaluation(
      @NonNull FeatureConfig featureConfig, Target target, @NonNull Variation variation);
}
