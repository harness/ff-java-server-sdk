package io.harness.cf.client.common;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class Utils {

  private Utils() {}

  public static void shutdownExecutorService(
      ExecutorService scheduler, Runnable successCallback, Consumer<String> failCallback) {
    scheduler.shutdown();
    final int TIMEOUT = 10;

    try {
      if (!scheduler.awaitTermination(TIMEOUT, SECONDS)) {
        scheduler.shutdownNow();
        if (scheduler.awaitTermination(TIMEOUT, SECONDS)) {
          successCallback.run();
        } else {
          failCallback.accept(
              "ScheduledExecutorService failed to terminate after " + TIMEOUT + " seconds");
        }
      } else {
        successCallback.run();
      }
    } catch (InterruptedException ie) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public static Map<String, String> redactHeaders(Map<String, String> hdrsMap) {
    if (hdrsMap == null || hdrsMap.isEmpty()) return Collections.emptyMap();
    HashMap<String, String> map = new HashMap<>(hdrsMap);
    map.computeIfPresent("Authorization", (k, v) -> "*");
    map.computeIfPresent("API-Key", (k, v) -> "*");
    return map;
  }

  public static boolean isEmpty(Collection<?> collection) {
    return collection == null || collection.isEmpty();
  }
}
