package io.harness.ff.examples;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.Event;
import io.harness.cf.client.api.FileMapStore;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
class Simple {
    private static final String SDK_KEY = System.getenv("SDK_KEY");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static CfClient client;

    public static void main(String... args) {

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    scheduler.shutdown();
                                    client.close();
                                }));

        final FileMapStore fileStore = new FileMapStore("Non-Freemium");
        client = new CfClient(SDK_KEY, Config.builder().store(fileStore).build());
        client.on(Event.READY, result -> log.info("READY"));

        client.on(Event.CHANGED, result -> log.info("Flag changed {}", result));

        final Target target =
                Target.builder()
                        .identifier("target1")
                        .isPrivate(false)
                        .attribute("testKey", "TestValue")
                        .name("target1")
                        .build();

        scheduler.scheduleAtFixedRate(
                () -> {
                    final boolean bResult = client.boolVariation("flag1", target, false);
                    log.info("Boolean variation: {}", bResult);
                    final JsonObject jsonResult = client.jsonVariation("flag4", target, new JsonObject());
                    log.info("JSON variation: {}", jsonResult);
                },
                0,
                10,
                TimeUnit.SECONDS);
    }
}
