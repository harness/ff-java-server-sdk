package io.harness.ff.examples;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.*;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExcessiveLogs {
//    private static final String SDK_KEY = "97432269-f803-4efa-bca9-36c22d77ad5d";
    private static final String SDK_KEY = "2e182b14-9944-4bd4-9c9f-3e859e2a2954";
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
        HarnessConfig config = HarnessConfig
                .builder()
                .configUrl("http://localhost:3000/api/1.0")
                .eventUrl("http://localhost:3000/api/1.0")
                .build();
        HarnessConnector connector = new HarnessConnector(SDK_KEY, config);
        client = new CfClient(connector, BaseConfig.builder()
                .debug(true)
                .analyticsEnabled(true)
                .streamEnabled(true)
                .build());
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
                    final boolean bResult = client.boolVariation("flag1", target, false);
                    MDC.put("flag", "flag1");
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
