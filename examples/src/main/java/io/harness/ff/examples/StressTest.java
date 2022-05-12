package io.harness.ff.examples;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FeatureFlagInitializeException;
import io.harness.cf.client.api.FileMapStore;
import io.harness.cf.client.dto.Target;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This application stress-test the SDK
 * with aggressive multi-threading execution.
 */
@Slf4j
public class StressTest {

    private static final String MOCK_SERVER_API_KEY;
    private static final HashMap<String, String> keys;
    private static final ScheduledExecutorService scheduler;

    static {

        MOCK_SERVER_API_KEY = "2e182b14-9944-4bd4-9c9f-3e859e2a2954";

        keys = new HashMap<>();
        keys.put("MOCK_SERVER_API", MOCK_SERVER_API_KEY);
        scheduler = Executors.newScheduledThreadPool(keys.size());
    }

    public static void main(String... args) {

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));

        for (final String keyName : keys.keySet()) {

            final String apiKey = keys.get(keyName);
            final FileMapStore fileStore = new FileMapStore(keyName);
            final Config.ConfigBuilder<?, ?> builder = Config.builder()
                    .configUrl("http://localhost:3000/api/1.0")
                    .eventUrl("http://localhost:3000/api/1.0");

            final CfClient client = new CfClient(apiKey, builder.store(fileStore).build());
            final String logPrefix = keyName + " :: " + client.hashCode() + " ";

            final String random = getRandom();

            final Target target = Target.builder()
                    .identifier("Target_" + random)
                    .isPrivate(false)
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

                        final boolean bResult = client.boolVariation("bool-flag", target, false);
                        log.info(logPrefix + "Boolean variation: {}", bResult);
                    },

                    0,
                    10,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    @NonNull
    private static String getRandom() {
        return String.valueOf(new Random().nextDouble());
    }
}
