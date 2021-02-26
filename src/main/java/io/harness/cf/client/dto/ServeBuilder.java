package io.harness.cf.client.dto;

import io.harness.cf.model.Distribution;
import io.harness.cf.model.Serve;

public final class ServeBuilder {
  private Distribution distribution = null;
  private String variation = null;

  private ServeBuilder() {}

  public static ServeBuilder aServe() {
    return new ServeBuilder();
  }

  public ServeBuilder distribution(Distribution distribution) {
    this.distribution = distribution;
    return this;
  }

  public ServeBuilder variation(String variation) {
    this.variation = variation;
    return this;
  }

  public Serve build() {
    Serve serve = new Serve();
    serve.setDistribution(distribution);
    serve.setVariation(variation);
    return serve;
  }
}
