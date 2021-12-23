package io.harness.cf.client;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Example {

  public static final String FEATURE_FLAG_KEY = "toggle";
  public static final String API_KEY = "your api key";

  public static void main(String... args) {
    CfClient cfClient =
        new CfClient(
            API_KEY,
            Config.builder()
                .configUrl("https://config.feature-flags.uat.harness.io/api/1.0")
                .eventUrl("https://event.feature-flags.uat.harness.io/api/1.0")
                .readTimeout(5000)
                .build());
    Target target =
        Target.builder()
            .identifier("target1")
            .isPrivate(false)
            .attribute("testKey", "TestValue")
            .name("target1")
            .build();
    while (true) {
      boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
      log.info("Boolean variation: {}", result);
      try {
        Thread.sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
