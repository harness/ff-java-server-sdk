package io.harness.cf.client.api.rules;

import static io.harness.cf.client.api.Evaluator.getAttrValue;

import com.google.common.base.Preconditions;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.Distribution;
import io.harness.cf.model.Serve;
import io.harness.cf.model.WeightedVariation;
import java.util.Objects;

public class DistributionProcessor {
  private final Distribution distribution;

  public DistributionProcessor(Serve serve) {
    this.distribution = serve.getDistribution();
    Preconditions.checkNotNull(distribution.getVariations());
  }

  public String loadKeyName(Target target) {
    String variation = "";
    for (WeightedVariation weightedVariation : distribution.getVariations()) {
      variation = weightedVariation.getVariation();
      if (isEnabled(target, Objects.requireNonNull(weightedVariation.getWeight()))) {
        return variation;
      }
    }
    // distance between last variation and total percentage
    return isEnabled(target, Strategy.ONE_HUNDRED) ? variation : "";
  }

  private boolean isEnabled(Target target, int percentage) {
    String bucketBy = distribution.getBucketBy();
    Object value = null;
    try {
      value = getAttrValue(target, distribution.getBucketBy());
    } catch (CfClientException e) {
      e.printStackTrace();
    }
    String identifier = Objects.requireNonNull(value).toString();

    if (identifier.equals("")) {
      return false;
    }

    Strategy strategy = new Strategy(identifier, bucketBy);
    int bucketId = strategy.loadNormalizedNumber();

    return percentage > 0 && bucketId <= percentage;
  }
}
