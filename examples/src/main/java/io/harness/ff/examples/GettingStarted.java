package io.harness.ff.examples;

import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GettingStarted {
    // API Key - set this as an env variable
    private static final String apiKey = getEnvOrDefault("FF_API_KEY", "");

    // Flag Identifier
    private static final String flagName = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        System.out.println("Harness SDK Getting Started");

        //Create a Feature Flag Client
        try (CfClient cfClient = new CfClient(apiKey, BaseConfig.builder().build())) {
            cfClient.waitForInitialization();

            // Create a target (different targets can get different results based on rules.  This includes a custom attribute 'location')
            final Target target = Target.builder()
                    .identifier("javasdk")
                    .name("JavaSDK")
                    .build();

            // Loop forever reporting the state of the flag
            scheduler.scheduleAtFixedRate(
                    () -> {
                        boolean result = cfClient.boolVariation(flagName, target, false);
                        System.out.println("Flag '" + flagName + "' Boolean variation is " + result);
                    },
                    0,
                    10,
                    TimeUnit.SECONDS);


            TimeUnit.MINUTES.sleep(15);

            // Close the SDK
            System.out.println("Cleaning up...");
            scheduler.shutdownNow();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get the value from the environment or return the default
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}