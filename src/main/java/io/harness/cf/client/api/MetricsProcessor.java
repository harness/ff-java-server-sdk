package io.harness.cf.client.api;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricsProcessor {

  @Setter private String environmentID;
  @Setter private String cluster;

  public void start() {}

  public void stop() {}
}
