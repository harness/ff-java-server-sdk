package io.harness.cf.client;

import io.harness.cf.client.api.Client;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.FileMapStore;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleExample {
  public static Client client;

  public static final String SDK_KEY = "1c100d25-4c3f-487b-b198-3b3d01df5794";

  @SuppressWarnings("InfiniteLoopStatement")
  public static void main(String... args) {

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  System.out.println("Closing application");
                  client.close();
                }));

    final FileMapStore fileStore = new FileMapStore("Non-Freemium");
    client = new Client(SDK_KEY, Config.builder().store(fileStore).build());

    Target target =
        Target.builder()
            .identifier("target1")
            .isPrivate(false)
            .attribute("testKey", "TestValue")
            .name("target1")
            .build();

    while (true) {
      final boolean bResult = client.boolVariation("test", target, false);
      log.info("Boolean variation: {}", bResult);

      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
