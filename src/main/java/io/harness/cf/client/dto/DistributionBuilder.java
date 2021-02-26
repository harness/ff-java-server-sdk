package io.harness.cf.client.dto;

import io.harness.cf.model.Distribution;
import io.harness.cf.model.WeightedVariation;
import java.util.List;

public final class DistributionBuilder {
  private String bucketBy = null;
  private List<WeightedVariation> variations = null;

  private DistributionBuilder() {}

  public static DistributionBuilder aDistribution() {
    return new DistributionBuilder();
  }

  public DistributionBuilder withBucketBy(String bucketBy) {
    this.bucketBy = bucketBy;
    return this;
  }

  public DistributionBuilder withVariations(List<WeightedVariation> variations) {
    this.variations = variations;
    return this;
  }

  public Distribution build() {
    Distribution distribution = new Distribution();
    distribution.setBucketBy(bucketBy);
    distribution.setVariations(variations);
    return distribution;
  }
}
