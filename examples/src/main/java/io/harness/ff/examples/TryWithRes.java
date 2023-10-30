package io.harness.ff.examples;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FeatureFlagInitializeException;
import io.harness.cf.client.api.XmlFileMapStore;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TryWithRes {
    private static final String SDK_KEY = System.getenv("SDK_KEY");

    public static void main(String... args) throws InterruptedException {

        try (final XmlFileMapStore fileStore = new XmlFileMapStore("Non-Freemium");
             final CfClient client = new CfClient(SDK_KEY, Config.builder().store(fileStore).build())) {
            client.waitForInitialization();

            final Target target =
                    Target.builder()
                            .identifier("target1")
                            .attribute("testKey", "TestValue")
                            .name("target1")
                            .build();

            final boolean bResult = client.boolVariation("test", target, false);
            log.info("Boolean variation: {}", bResult);
            final JsonObject jsonResult = client.jsonVariation("flag4", target, new JsonObject());
            log.info("JSON variation: {}", jsonResult);
        } catch (FeatureFlagInitializeException e) {
            log.error("Exception: {}", e.getMessage());
        }
    }
}
