package io.harness.cf.client.example;

import com.google.common.collect.ImmutableMap;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Example {

  // public static final String FEATURE_FLAG_KEY = "toggle";
  public static final String FEATURE_FLAG_KEY = "show_animation";
  public static final String COLOR_FEATURE_KEY = "color";
  public static final String COUNT_FEATURE_KEY = "count";

  public static void main(String... args) throws Exception {

    Target target =
        Target.builder()
            .identifier("target2")
            // .identifier("hannah")
            .attributes(
                new ImmutableMap.Builder<String, Object>()
                    .put("accountId", "kmpySmUISimoRrJL6NL73w")
                    .put("name", "ObjectName5")
                    .put("licenseType", "TRIAL")
                    .build())
            .isPrivate(false)
            .name("tang")
            .build();
    Target target1 =
        Target.builder()
            .identifier("subir")
            .attributes(
                new ImmutableMap.Builder<String, Object>()
                    .put("accountId", "ampySmUISimoRrJL1NL73u")
                    .put("name", "ObjectName1")
                    .put("licenseType", "TEST")
                    .build())
            .name("adhikari")
            .isPrivate(true)
            .build();

    // local
    String apiKey = "8b3bf1d0-6c88-4cdb-99a1-36aff131911a";
    CfClient cfClient =
        new CfClient(apiKey, Config.builder().baseUrl("http://localhost:7999/api/1.0").build());

    // qb
    //        String apiKey = "f2b6549c-3e1d-482e-bdd5-1e984cdc6c46";
    //        CfClient cfClient =
    //            new CfClient(apiKey,
    //     Config.builder().baseUrl("http://34.82.119.242:80/api/1.0").build());

    // qa
    //    String apiKey = "d7596c01-0eb0-4d1a-b1e0-a686a2851b29";
    //    CfClient cfClient =
    //        new CfClient(apiKey,
    // Config.builder().baseUrl("http://35.199.167.179:80/api/1.0").build());

    while (true) {
      boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
      log.info("Boolean variation: {}", result);
      Thread.sleep(3000);

      //      boolean result1 = cfClient.boolVariation(FEATURE_FLAG_KEY, target1, false);
      //      log.info("Boolean variation: {}", result1);
      //      Thread.sleep(3000);
      //
      //      boolean result2 = cfClient.boolVariation(FEATURE_FLAG_KEY, target1, false);
      //      log.info("Boolean variation: {}", result2);
      //      Thread.sleep(3000);

      /*      String color = cfClient.stringVariation(FEATURE_FLAG_KEY, target, "black");
      log.info("String variation: {}", color);
      Thread.sleep(2000);

      double count = cfClient.numberVariation(COUNT_FEATURE_KEY, target, 1);
      log.info("Number variation: {}", (int) count);
      Thread.sleep(2000);*/
    }
  }
}
