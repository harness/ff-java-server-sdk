package io.harness.ff.examples;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.Event;
import io.harness.cf.client.api.XmlFileMapStore;
import io.harness.cf.client.connector.LocalConnector;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Local {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static CfClient client;

    public static void main(String... args) throws InterruptedException {

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    scheduler.shutdown();
                                    client.close();
                                }));

        final XmlFileMapStore fileStore = new XmlFileMapStore("Non-Freemium");
        LocalConnector connector = new LocalConnector("./local");
        client = new CfClient(connector, Config.builder().store(fileStore).build());
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
                    final Number numResult = client.numberVariation("flag2", target, 1);
                    log.info("Number variation: {}", numResult);
                },
                0,
                10,
                TimeUnit.SECONDS);
    }
}
