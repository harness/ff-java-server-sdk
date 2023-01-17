package io.harness.cf.client.api.testutils;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicLong;

public class PollingAtomicLong extends AtomicLong {
  private final int minValueToWaitFor;

  public PollingAtomicLong(int minValueToWaitFor) {
    this.minValueToWaitFor = minValueToWaitFor;
  }

  public void waitForMinimumValueToBeReached(int waitTimeSeconds, String name, String failMsg)
      throws InterruptedException {
    final int delayMs = 100;
    int maxWaitTime = (waitTimeSeconds * 1000) / delayMs;
    while (maxWaitTime > 0 && get() < minValueToWaitFor) {

      System.out.printf(
          "Waiting for " + name + " connections: got %d of min %d...\n", get(), minValueToWaitFor);
      Thread.sleep(delayMs);
      maxWaitTime--;
    }

    if (get() < minValueToWaitFor) {
      fail(failMsg);
    }
  }
}
