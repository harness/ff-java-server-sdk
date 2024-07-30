package io.harness.ff.examples;

import io.harness.cf.client.api.BaseConfig;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Event;
import io.harness.cf.client.api.XmlFileMapStore;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureSnapshot;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class EventExamplePoC {
    private static final String SDK_KEY = "";
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

        final XmlFileMapStore fileStore = new XmlFileMapStore("Non-Freemium");
        // this is one way of initialising config.
        final HarnessConnector hc =
                new HarnessConnector( SDK_KEY,
                        HarnessConfig.builder().build());

        BaseConfig bc = BaseConfig.builder().
                enableFeatureSnapshot(true).
                build();

        // initialise the client.
        client = new CfClient(hc,bc);

        client.on(Event.READY, result -> log.info("READY"));

        // example of specified prefix we can filter on.
        final String FLAG_PREFIX="FFM_";
        // given flag change event - get both previous and current feature if prefix is matched.
        client.on(Event.CHANGED, identifier ->{
                if (identifier.startsWith(FLAG_PREFIX)){
                    log.info("We had a chang event and prefix matched, lets inspect the diff");
                    // fetch current and previous version of the feature
                    FeatureSnapshot snapshot = client.getFeatureSnapshot(identifier);
                    log.info("Previous flag config: {}, {}",identifier, snapshot.getPrevious());
                    log.info("Current flag config: {}, {}",identifier, snapshot.getCurrent());
                }else{
                    log.info("We had an event change but flag did not have required prefix");
                }
        });

        final Target target =
                Target.builder()
                        .identifier("target1")
                        .attribute("testKey", "TestValue")
                        .name("target1")
                        .build();

        scheduler.scheduleAtFixedRate(
                () -> {
                    log.info("ticking...");
                },
                0,
                10,
                TimeUnit.SECONDS);

    }
}
