package io.harness.cf.client.api;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricsProcessor {

  private MetricsCallback callback;

  public MetricsProcessor(MetricsCallback callback) {
    this.callback = callback;

    this.callback.onMetricsReady();
  }

  @Setter private String environmentID;
  @Setter private String cluster;

  public void start() {}

  public void stop() {}
}
