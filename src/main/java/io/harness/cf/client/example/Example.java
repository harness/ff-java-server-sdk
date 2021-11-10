package io.harness.cf.client.example;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.Client;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FileMapStore;
import io.harness.cf.client.dto.Target;

import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class Example {

    public static final int capacity;
    public static final Executor executor;
    public static final HashMap<String, String> keys;

    public static final String FREEMIUM_API_KEY = "45d2a13a-c62f-4116-a1a7-86f25d715a2e";
    public static final String NON_FREEMIUM_API_KEY = "cf355e7a-2ac6-4304-90fa-d88d9c7f983d";

    static {
        capacity = 5;
        keys = new HashMap<>(capacity);
        keys.put("Freemium", FREEMIUM_API_KEY);
        keys.put("Non-Freemium", NON_FREEMIUM_API_KEY);
        executor = Executors.newFixedThreadPool(2);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(String... args) {

        for (final String keyName : keys.keySet()) {

            executor.execute(
                    () -> {
                        final String apiKey = keys.get(keyName);
                        final FileMapStore fileStore = new FileMapStore(keyName);
                        final Client client = new Client(apiKey, Config.builder().store(fileStore).build());
                        final String logPrefix = keyName + " :: " + client.hashCode() + " ";

                        Target target =
                                Target.builder()
                                        .identifier("target1")
                                        .isPrivate(false)
                                        .attribute("testKey", "TestValue")
                                        .name("target1")
                                        .build();

                        while (true) {
                            final boolean bResult = client.boolVariation("flag1", target, false);
                            log.info(logPrefix + "Boolean variation: {}", bResult);

                            final double dResult = client.numberVariation("flag2", target, -1);
                            log.info(logPrefix + "Number variation: {}", dResult);

                            final String sResult = client.stringVariation("flag3", target, "NO_VALUE!!!");
                            log.info(logPrefix + "String variation: {}", sResult);

                            final JsonObject jResult = client.jsonVariation("flag4", target, new JsonObject());
                            log.info(logPrefix + "JSON variation: {}", jResult);

                            try {

                                Thread.sleep(30 * 1000);
                            } catch (InterruptedException e) {

                                e.printStackTrace();
                            }
                        }
                    });
        }

        Thread.yield();
    }
}