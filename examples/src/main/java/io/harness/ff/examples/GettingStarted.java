package io.harness.ff.examples;

import io.harness.cf.client.api.*;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.dto.Target;

public class GettingStarted {
    // API Key - set this as an env variable
    private static String apiKey = getEnvOrDefault("FF_API_KEY", "");

    // Flag Identifier
    private static String flagName = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");

    public static void main(String[] args) {
        System.out.println("Harness SDK Getting Started");

        try {
            //Create a Feature Flag Client
            CfClient cfClient = new CfClient(new HarnessConnector(apiKey, HarnessConfig.builder().build()));
            cfClient.waitForInitialization();

            // Create a target (different targets can get different results based on rules.  This includes a custom attribute 'location')
            final Target target = Target.builder()
                    .identifier("javasdk")
                    .name("JavaSDK")
                    .attribute("location", "emea")
                    .build();

            // Loop forever reporting the state of the flag
            while (true) {
                boolean result = cfClient.boolVariation(flagName, target, false);
                System.out.println("Boolean variation is " + result);
                Thread.sleep(2000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close the SDK
            CfClient.getInstance().close();
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