package io.harness.ff.examples;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.FeatureFlagInitializeException;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConfigExample {
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

        final HarnessConnector hc =
                new HarnessConnector(
                        SDK_KEY,
                        HarnessConfig.builder()
                                .configUrl("http://localhost:3000/api/1.0")
                                .eventUrl("http://localhost:3000/api/1.0")
                                .build());
        client = new CfClient(hc);
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
