package io.harness.cf.client;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
class Example {

    public static final boolean FREEMIUM = false;
    public static final String FREEMIUM_API_KEY = "1acfded6-65b9-4e0a-9cbd-a6abd7574f54";
    public static final String NON_FREEMIUM_API_KEY = "1acfded6-65b9-4e0a-9cbd-a6abd7574f54";

    private static String getApiKey() {

        if (FREEMIUM) {

            return FREEMIUM_API_KEY;
        } else {

            return NON_FREEMIUM_API_KEY;
        }
    }

    public static void main(String... args) {

        final CfClient cfClient = CfClient.getInstance();
        final CountDownLatch latch = new CountDownLatch(1);

        cfClient.initialize(

                getApiKey(),
                (success, error) -> {

                    if (success) {

                        latch.countDown();
                        log.info("Init success");
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

        final boolean bResult = cfClient.boolVariation("flag1", target, false);
        log.info("Boolean variation: {}", bResult);

        final double dResult = cfClient.numberVariation("flag2", target, -1);
        log.info("Number variation: {}", dResult);

        final String sResult = cfClient.stringVariation("flag3", target, "NO_VALUE!!!");
        log.info("String variation: {}", sResult);
    }
}
