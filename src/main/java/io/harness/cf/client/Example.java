package io.harness.cf.client;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
class Example {

    public static final int capacity;
    public static final Executor executor;
    public static final HashMap<String, String> keys;

    public static final String FREEMIUM_API_KEY = "dac123e6-8a08-4775-825b-ed92583b5a70";
    public static final String NON_FREEMIUM_API_KEY = "1acfded6-65b9-4e0a-9cbd-a6abd7574f54";

    static {

        capacity = 5;
        keys = new HashMap<>(capacity);
        keys.put("Freemium", FREEMIUM_API_KEY);
        keys.put("Non-Freemium", NON_FREEMIUM_API_KEY);
        executor = Executors.newSingleThreadExecutor();
    }

    public static void main(String... args) {

        for (final String keyName : keys.keySet()) {

            executor.execute(() -> {

                final String apiKey = keys.get(keyName);
                final CfClient cfClient = new CfClient();
                final String logPrefix = keyName + " :: " + cfClient.hashCode() + " ";
                final CountDownLatch latch = new CountDownLatch(1);

                try {
                    cfClient.initialize(

                            apiKey,
                            (success, error) -> {

                                if (success) {

                                    latch.countDown();
                                    log.info(logPrefix + "Init success");
                                    return;
                                }

                                if (error != null) {

                                    log.error(logPrefix + "Error", error);
                                }
                                System.exit(1);
                            }
                    );
                } catch (IllegalStateException e) {

                    log.error(logPrefix + "Error", e);
                    System.exit(1);
                }

                try {

                    final boolean result = latch.await(30, TimeUnit.SECONDS);
                    if (!result) {

                        log.error(logPrefix + "Initialization result is faulty");
                        System.exit(1);
                    }
                } catch (InterruptedException e) {

                    log.error(logPrefix + "Error", e);
                    System.exit(1);
                }

                Target target = Target.builder()
                        .identifier("target1")
                        .isPrivate(false)
                        .attribute("testKey", "TestValue")
                        .name("target1")
                        .build();

                final boolean bResult = cfClient.boolVariation("flag1", target, false);
                log.info(logPrefix + "Boolean variation: {}", bResult);

                final double dResult = cfClient.numberVariation("flag2", target, -1);
                log.info(logPrefix + "Number variation: {}", dResult);

                final String sResult = cfClient.stringVariation("flag3", target, "NO_VALUE!!!");
                log.info(logPrefix + "String variation: {}", sResult);
            });
        }

        Thread.yield();
    }
}
