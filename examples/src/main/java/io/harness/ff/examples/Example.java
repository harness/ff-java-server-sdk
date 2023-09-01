package io.harness.ff.examples;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Example {
    private static final int capacity;
    private static final HashMap<String, String> keys;
    private static final ScheduledExecutorService scheduler;

    private static final String UAT_API_KEY = System.getenv("UAT_SDK_KEY");
    private static final String FREEMIUM_API_KEY = System.getenv("FREEMIUM_SDK_KEY");
    private static final String FREEMIUM_API_KEY_2 = System.getenv("FREEMIUM_SDK_KEY_2");
    private static final String NON_FREEMIUM_API_KEY = System.getenv("NON_FREEMIUM_SDK_KEY");
    private static final String NON_FREEMIUM_API_KEY_2 = System.getenv("NON_FREEMIUM_SDK_KEY_2");

    static {
        capacity = 5;
        keys = new HashMap<>(capacity);
        keys.put("UAT", UAT_API_KEY);
        keys.put("Freemium", FREEMIUM_API_KEY);
        keys.put("Freemium-2", FREEMIUM_API_KEY_2);
        keys.put("Non-Freemium", NON_FREEMIUM_API_KEY);
        keys.put("Non-Freemium-2", NON_FREEMIUM_API_KEY_2);
        scheduler = Executors.newScheduledThreadPool(keys.size());
    }

    public static void main(String... args) {

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));

        for (final String keyName : keys.keySet()) {

            log.info("SDK key: " + keyName + ", " + keys.get(keyName));
        }

        for (final String keyName : keys.keySet()) {

            final String apiKey = keys.get(keyName);
            final XmlFileMapStore fileStore = new XmlFileMapStore(keyName);
            final Config.ConfigBuilder<?, ?> builder = Config.builder();

            if (keyName.equals("UAT")) {

                builder
                        .configUrl("https://config.feature-flags.uat.harness.io/api/1.0")
                        .eventUrl("https://event.feature-flags.uat.harness.io/api/1.0");
            }

            final CfClient client = new CfClient(apiKey, builder.store(fileStore).build());
            final String logPrefix = keyName + " :: " + client.hashCode() + " ";

            final String random = getRandom();

            final Target target =
                    Target.builder()
                            .identifier("Target_" + random)
                            .isPrivate(false)
                            .attribute("Test_key_" + getRandom(), getRandom())
                            .attribute("Test_key_" + getRandom(), getRandom())
                            .attribute("Test_key_" + getRandom(), getRandom())
                            .name("Target_" + random)
                            .build();

            try {

                client.waitForInitialization();

            } catch (InterruptedException | FeatureFlagInitializeException e) {

                log.error(e.getMessage());
                System.exit(1);
            }

            log.info("Client is ready: " + keyName + ", " + client.hashCode());

            scheduler.scheduleAtFixedRate(
                    () -> {
                        final boolean bResult = client.boolVariation("flag1", target, false);
                        log.info(logPrefix + "Boolean variation: {}", bResult);

                        final double dResult = client.numberVariation("flag2", target, -1);
                        log.info(logPrefix + "Number variation: {}", dResult);

                        final String sResult = client.stringVariation("flag3", target, "NO_VALUE!!!");
                        log.info(logPrefix + "String variation: {}", sResult);

                        final JsonObject jResult = client.jsonVariation("flag4", target, new JsonObject());
                        log.info(logPrefix + "JSON variation: {}", jResult);
                    },
                    0,
                    10,
                    TimeUnit.SECONDS);
        }

        Thread.yield();
    }

    @NonNull
    private static String getRandom() {
        return String.valueOf(new Random().nextDouble());
    }
}
