package io.harness.cf.client;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
class Example {

    public static final String API_KEY = "dummyKey";
    public static final String FEATURE_FLAG_KEY = "toggle";

    public static void main(String... args) {

        final CfClient cfClient = CfClient.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);

        cfClient.initialize(

                API_KEY,
                (success, error) -> {

                    if (success) {

                        latch.countDown();
                        return;
                    }

                    if (error != null) {

                        log.error("Error", error);
                    }
                    System.exit(1);
                }
        );

        try {

            final boolean result = latch.await(30, TimeUnit.SECONDS);
            if (!result) {

                log.error("Initialization result is faulty");
                System.exit(1);
            }
        } catch (InterruptedException e) {

            log.error("Error", e);
            System.exit(1);
        }

        Target target = Target.builder()
                .identifier("target1")
                .isPrivate(false)
                .attribute("testKey", "TestValue")
                .name("target1")
                .build();

        boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
        log.info("Boolean variation: {}", result);
    }
}
