package io.harness.cf.client.common;

import static java.util.concurrent.TimeUnit.SECONDS;

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
}
