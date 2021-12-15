package io.harness.cf.client.example;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FeatureFlagInitializeException;
import io.harness.cf.client.api.FileMapStore;
import io.harness.cf.client.dto.Target;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
class Example {

  private static final int capacity;
  private static final HashMap<String, String> keys;
  private static final ScheduledExecutorService scheduler;

  private static final String FREEMIUM_API_KEY = "45d2a13a-c62f-4116-a1a7-86f25d715a2e";
  private static final String FREEMIUM_API_KEY_2 = "75821279-f8f7-49b2-9f2b-b10c2ba36a32";
  private static final String NON_FREEMIUM_API_KEY = "9ecc4ced-afc1-45af-9b54-c899cbff4b62";
  private static final String NON_FREEMIUM_API_KEY_2 = "32ba37eb-2c12-4143-9d05-fb4d6782b083";

  static {
    capacity = 5;
    keys = new HashMap<>(capacity);
    keys.put("Freemium", FREEMIUM_API_KEY);
    keys.put("Freemium-2", FREEMIUM_API_KEY_2);
    keys.put("Non-Freemium", NON_FREEMIUM_API_KEY);
    keys.put("Non-Freemium-2", NON_FREEMIUM_API_KEY_2);
    scheduler = Executors.newScheduledThreadPool(keys.size());
  }

  public static void main(String... args) {

    Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));

    for (final String keyName : keys.keySet()) {

      final String apiKey = keys.get(keyName);
      final FileMapStore fileStore = new FileMapStore(keyName);
      final CfClient client = new CfClient(apiKey, Config.builder().store(fileStore).build());
      final String logPrefix = keyName + " :: " + client.hashCode() + " ";

      final String random = getRandom();

      final Target target =
          Target.builder()
              .identifier("Target_" + random)
              .isPrivate(false)
              .attribute("Test_key_" + getRandom(), getRandom())
              .attribute("Test_key_" + getRandom(), getRandom())
              .attribute("Test_key_" + getRandom(), getRandom())
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
            final boolean bResult = client.boolVariation("flag1", target, false);
            log.info(logPrefix + "Boolean variation: {}", bResult);

            final double dResult = client.numberVariation("flag2", target, -1);
            log.info(logPrefix + "Number variation: {}", dResult);

            final String sResult = client.stringVariation("flag3", target, "NO_VALUE!!!");
            log.info(logPrefix + "String variation: {}", sResult);

            final JsonObject jResult = client.jsonVariation("flag4", target, new JsonObject());
            log.info(logPrefix + "JSON variation: {}", jResult);
          },
          0,
          10,
          TimeUnit.SECONDS);
    }

    Thread.yield();
  }

  @NotNull
  private static String getRandom() {
    return String.valueOf(new Random().nextDouble());
  }
}
