package io.harness.cf.client.example;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Singleton {
  private static final String SDK_KEY = System.getenv("SDK_KEY");
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private static CfClient client;

  public static void main(String... args)
      throws InterruptedException, FeatureFlagInitializeException {

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  scheduler.shutdown();
                  client.close();
                }));

    final FileMapStore fileStore = new FileMapStore("Non-Freemium");
    client = CfClient.getInstance();
    client.initialize(SDK_KEY, Config.builder().store(fileStore).build());
    client.waitForInitialization();

    final Target target =
        Target.builder()
            .identifier("target1")
            .isPrivate(false)
            .attribute("testKey", "TestValue")
            .name("target1")
            .build();

    scheduler.scheduleAtFixedRate(
        () -> {
          final boolean bResult = client.boolVariation("flag1", target, false);
          log.info("Boolean variation: {}", bResult);
          final JsonObject jsonResult = client.jsonVariation("flag4", target, new JsonObject());
          log.info("JSON variation: {}", jsonResult);
        },
        0,
        10,
        TimeUnit.SECONDS);
  }
}
