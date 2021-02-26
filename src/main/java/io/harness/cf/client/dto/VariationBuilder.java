package io.harness.cf.client.dto;

import io.harness.cf.model.Variation;

public final class VariationBuilder {
  private String identifier;
  private String value;
  private String name;
  private String description;

  private VariationBuilder() {}

  public static VariationBuilder aVariation() {
    return new VariationBuilder();
  }

  public VariationBuilder identifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public VariationBuilder value(String value) {
    this.value = value;
    return this;
  }

  public VariationBuilder name(String name) {
    this.name = name;
    return this;
  }

  public VariationBuilder description(String description) {
    this.description = description;
    return this;
  }

  public Variation build() {
    Variation variation = new Variation();
    variation.setIdentifier(identifier);
    variation.setValue(value);
    variation.setName(name);
    variation.setDescription(description);
    return variation;
  }
}
