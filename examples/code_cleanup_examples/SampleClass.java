package io.harness.ff.code_cleanup_examples;

import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;

class SampleJava {
    enum Flags {
        STALE_FLAG,
        OTHER_FLAG
    }

    private static final String SDK_KEY = System.getenv("SDK_KEY");
    private static CfClient client;

    public static void main(String... args) throws FeatureFlagInitializeException, InterruptedException {
        client = new CfClient(SDK_KEY, Config.builder().store(fileStore).build());
        client.waitForInitialization();

        final Target target =
                Target.builder()
                        .identifier("target1")
                        .isPrivate(false)
                        .attribute("testKey", "TestValue")
                        .name("target1")
                        .build();


        if (client.boolVariation(Flags.STALE_FLAG)) {
            System.out.println("STALE_FLAG is true code path");
        } else {
            System.out.println("STALE_FLAG is false code path");
        }

        if (client.boolVariation(Flags.OTHER_FLAG)) {
            System.out.println("OTHER_FLAG is true code path");
        } else {
            System.out.println("OTHER_FLAG is false code path");
        }

    }

}
