package io.harness.cf.client;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Example {

  public static final String FEATURE_FLAG_KEY = "toggle";
  public static final String API_KEY = "dummyKey";

  public static void main(String... args) {

    final CfClient cfClient = CfClient.getInstance();
    cfClient.initialize(API_KEY);

    Target target =
        Target.builder()
            .identifier("target1")
            .isPrivate(false)
            .attribute("testKey", "TestValue")
            .name("target1")
            .build();

    boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
    log.info("Boolean variation: {}", result);
  }
}
