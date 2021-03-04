package io.harness.cf.client.example;

import com.google.common.collect.ImmutableMap;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Example {

  public static final String FEATURE_FLAG_KEY = "toggle";
  public static final String API_KEY = "8b3bf1d0-6c88-4cdb-99a1-36aff131911a";

  public static void main(String... args) {
    CfClient cfClient = new CfClient(API_KEY);
    Target target =
        Target.builder()
            .identifier("target1")
            .attributes(
                new ImmutableMap.Builder<String, Object>().put("licenseType", "TRIAL").build())
            .isPrivate(false)
            .name("target1")
            .build();
    boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
    log.info("Boolean variation: {}", result);
  }
}
