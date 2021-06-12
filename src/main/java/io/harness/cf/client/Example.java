package io.harness.cf.client;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Target;
import java.util.HashMap;

class Example {

  // public static final String FEATURE_FLAG_KEY = "firstbooleanflag";
  // public static final String FEATURE_FLAG_KEY = "test_key";
  // public static final String API_KEY = "8662b1a8-3ebb-4d24-8b31-19d72bae276a";
  // public static final String API_KEY = "e49cd000-eefd-4861-84ed-332dc14c0977";

  public static final String FEATURE_FLAG_KEY = "harnessappdemodarkmode";
  public static final String API_KEY = "65e67c96-6e3b-42da-a4dc-65b8236bef88";

  public static void main(String... args) {
    try {
      /** Put the API Key here from your environment */
      CfClient cfClient = new CfClient(API_KEY, Config.builder().streamEnabled(true).build());
      /** Define you target on which you would like to evaluate the featureFlag */
      Target target =
          Target.builder()
              .name("User1")
              .attributes(new HashMap<String, Object>())
              .identifier("user1@example.com")
              .build();
      while (true) {
        /** Sleep for sometime before printing the value of the flag */
        Thread.sleep(2000);
        /**
         * This is a sample boolean flag. You can replace the flag value with the identifier of your
         * feature flag
         */
        boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
        System.out.println("Boolean variation is " + result);
      }
    } catch (Exception e) {

      e.printStackTrace();
    }
  }
}
