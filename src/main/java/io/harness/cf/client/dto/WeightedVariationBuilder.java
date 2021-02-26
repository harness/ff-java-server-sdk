package io.harness.cf.client.dto;

import io.harness.cf.model.WeightedVariation;

public final class WeightedVariationBuilder {
  private String variation;
  private Integer weight;

  private WeightedVariationBuilder() {}

  public static WeightedVariationBuilder aWeightedVariation() {
    return new WeightedVariationBuilder();
  }

  public WeightedVariationBuilder withVariation(String variation) {
    this.variation = variation;
    return this;
  }

  public WeightedVariationBuilder withWeight(Integer weight) {
    this.weight = weight;
    return this;
  }

  public WeightedVariation build() {
    WeightedVariation weightedVariation = new WeightedVariation();
    weightedVariation.setVariation(variation);
    weightedVariation.setWeight(weight);
    return weightedVariation;
  }
}
