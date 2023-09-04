package io.harness.ff.examples;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
class Simple {
    private static final String SDK_KEY = System.getenv("SDK_KEY");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static CfClient client;

    public static void main(String... args) throws FeatureFlagInitializeException, InterruptedException {

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    scheduler.shutdown();
                                    client.close();
                                }));

        final XmlFileMapStore fileStore = new XmlFileMapStore("Non-Freemium");
        client = new CfClient(SDK_KEY, Config.builder().store(fileStore).build());
        client.waitForInitialization();

        final Target target =
                Target.builder()
                        .identifier("target1")
                        .isPrivate(false)
                        .attribute("testKey", "TestValue")
                        .name("target1")
                        .build();

        scheduler.scheduleAtFixedRate(
                () -> {
                    final boolean bResult = client.boolVariation("harnessappdemoenablecimodule", target, false);
                    MDC.put("flag", "harnessappdemoenablecimodule");
                    MDC.put("target", target.getIdentifier());
                    log.info("Boolean variation: {}", bResult);

                    final JsonObject jsonResult = client.jsonVariation("flag4", target, new JsonObject());
                    MDC.put("flag", "flag4");
                    MDC.put("target", target.getIdentifier());
                    log.info("JSON variation: {}", jsonResult);
                },
                0,
                10,
                TimeUnit.SECONDS);
    }
}
