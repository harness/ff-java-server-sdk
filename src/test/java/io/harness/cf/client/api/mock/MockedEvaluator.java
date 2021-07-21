package io.harness.cf.client.api.mock;

import io.harness.cf.client.Evaluation;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;

public class MockedEvaluator implements Evaluation {

  @Override
  public Variation evaluate(FeatureConfig featureConfig, Target target) {

    return new Variation();
  }
}
