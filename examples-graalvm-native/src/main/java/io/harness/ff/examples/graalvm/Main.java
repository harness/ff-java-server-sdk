package io.harness.ff.examples.graalvm;

import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    private static String apiKey = getEnvOrDefault("FF_API_KEY", "");
    private static String flagName = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");

    public static void main(String[] args) {
        System.out.println("Harness SDK native example");

        try (CfClient cfClient = new CfClient(apiKey, BaseConfig.builder().build())) {
            cfClient.waitForInitialization();

            final Target target = Target.builder()
                    .identifier("javasdk-GraalVM")
                    .name("JavaSDK native")
                    .build();

            boolean result = cfClient.boolVariation(flagName, target, false);
            System.out.println("Flag '" + flagName + "' is " + result);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}