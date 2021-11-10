package io.harness.cf.client;

import com.google.gson.JsonObject;
import io.harness.cf.client.api.Client;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FileMapStore;
import io.harness.cf.client.dto.Target;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleExample {

  private static final String SDK_KEY = "1c100d25-4c3f-487b-b198-3b3d01df5794";
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public static void main(String... args) throws InterruptedException {

    Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));

    final FileMapStore fileStore = new FileMapStore("Non-Freemium");
    Client client = new Client(SDK_KEY, Config.builder().store(fileStore).build());
    client.waitForInitialization();

    Target target =
        Target.builder()
            .identifier("target1")
            .isPrivate(false)
            .attribute("testKey", "TestValue")
            .name("target1")
            .build();

    scheduler.scheduleAtFixedRate(
        () -> {
          final JsonObject bResult = client.jsonVariation("flag4", target, new JsonObject());
          log.info("JSON variation: {}", bResult);
        },
        0,
        10,
        TimeUnit.SECONDS);
  }
}
