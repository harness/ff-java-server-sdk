package io.harness.ff.examples;

import io.harness.cf.client.api.*;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.client.dto.Target;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GettingStarted {
    // API Key - set this as an env variable
    private static final String apiKey = getEnvOrDefault("FF_API_KEY", "xxx-xxx-xxx");

    // Flag Identifier
    private static final String flagName1 = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");
    private static final String flagName2 = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemoenableglobalhelp");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        System.out.println("Harness SDK Getting Started");

        BaseConfig options = BaseConfig.builder()
                .pollIntervalInSeconds(60)
                .streamEnabled(true)
                .analyticsEnabled(true)
                .build();

        HarnessConfig connectorConfig =    HarnessConfig.builder()
                .build();

        CfClient cfClient1 = new CfClient(new HarnessConnector(apiKey, connectorConfig), options);
        CfClient cfClient2 = new CfClient(new HarnessConnector(apiKey, connectorConfig), options);

        try {
            cfClient1.waitForInitialization();
            cfClient2.waitForInitialization();

            // Create a target (different targets can get different results based on rules.  This includes a custom attribute 'location')
            final Target target = Target.builder()
                    .identifier("javasdk")
                    .name("JavaSDK")
                    .build();

            // Loop forever reporting the state of the flag
            scheduler.scheduleAtFixedRate(
                    () -> {
                        boolean result = cfClient1.boolVariation(flagName1, target, false);
                        System.out.println("Flag '" + flagName1 + "' Client1 Boolean variation is " + result);
                        boolean result2 = cfClient2.boolVariation(flagName2, target, false);
                        System.out.println("Flag '" + flagName2 + "' Client2 Boolean variation is " + result2);
                    },
                    0,
                    10,
                    TimeUnit.SECONDS);

            // SDK will exit after 15 minutes, this gives the example time to stream events
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