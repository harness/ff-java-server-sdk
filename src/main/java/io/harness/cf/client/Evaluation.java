package io.harness.cf.client;

import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;

public interface Evaluation {

  Variation evaluate(FeatureConfig featureConfig, Target target) throws CfClientException;
}
