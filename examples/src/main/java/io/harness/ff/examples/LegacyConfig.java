package io.harness.ff.examples;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FeatureFlagInitializeException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LegacyConfig {
    private static final String SDK_KEY = System.getenv("SDK_KEY");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static CfClient client;

    public static void main(String... args)
            throws InterruptedException, FeatureFlagInitializeException {

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    scheduler.shutdown();
                                    client.close();
                                }));

        client =
                new CfClient(
                        SDK_KEY,
                        Config.builder()
                                .configUrl("http://localhost:9090/api/1.0")
                                .eventUrl("http://localhost:9090/api/1.0")
                                .build());
        client.waitForInitialization();

        scheduler.scheduleAtFixedRate(
                () -> {
                    final boolean bResult = client.boolVariation("bool-flag", null, false);
                    log.info("Boolean variation: {}", bResult);
                },
                0,
                10,
                TimeUnit.SECONDS);
    }
}
