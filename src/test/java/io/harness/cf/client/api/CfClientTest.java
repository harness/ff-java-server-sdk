package io.harness.cf.client.api;

import com.google.common.collect.ImmutableMap;
import io.harness.cf.ApiException;
import io.harness.cf.client.dto.Target;
import java.io.IOException;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Ignore;
import org.junit.Test;

@Slf4j
public class CfClientTest {

  private final CfClient cfClient;
  private Target target =
      Target.builder()
          .identifier("target1-identifier")
          .custom(
              new ImmutableMap.Builder<String, Object>()
                  .put("accountId", "kmpySmUISimoRrJL6NL73w")
                  .put("name", "ObjectName5")
                  .put("licenseType", "TRIAL")
                  .build())
          .build();

  public CfClientTest()
      throws CfClientException, ApiException, IOException, XmlPullParserException {
    // local
    String apiKey = "76320784-d40b-4d26-ac31-f1703c56f478";
    cfClient =
        new CfClient(
            apiKey,
            Config.builder().baseUrl("http://localhost:7999/api/1.0").streamEnabled(true).build());

    // qb
    //      String apiKey = "1734c427-0a9f-4e2e-92a7-5e04c7dec151";
    //      cfClient = new CfClient(apiKey, Config.builder()
    //              .baseUrl("https://qb.harness.io/cf").build());

    // uat
    //    String apiKey = "b36d5e5a-4fa8-11eb-ae93-0242ac130002";
    //    cfClient = new CfClient(apiKey);
  }

  @Test
  @Ignore
  public void boolVariation() {
    // String FEATURE_FLAG_KEY = "Test";
    // String FEATURE_FLAG_KEY = "show_animation";
    String FEATURE_FLAG_KEY = "enable_anomaly_detection_batch_job";

    IntStream.range(0, 500)
        .forEach(
            i -> {
              boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
              log.info("Boolean variation: {}", result);
              try {
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });
  }

  @Test
  @Ignore
  public void stringVariation() {
    String COLOR_FEATURE_KEY = "color";

    IntStream.range(0, 50)
        .forEach(
            i -> {
              String color = cfClient.stringVariation(COLOR_FEATURE_KEY, target, "black");
              log.info("String variation: {}", color);
              try {
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });
  }

  @Test
  @Ignore
  public void numberVariation() {
    String COUNT_FEATURE_KEY = "count";

    IntStream.range(0, 50)
        .forEach(
            i -> {
              String color = cfClient.stringVariation(COUNT_FEATURE_KEY, target, "black");
              log.info("String variation: {}", color);
              try {
                Thread.sleep(2000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            });
  }
}
