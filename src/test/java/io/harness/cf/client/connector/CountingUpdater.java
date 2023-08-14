package io.harness.cf.client.connector;

import io.harness.cf.client.api.testutils.PollingAtomicLong;
import io.harness.cf.client.dto.Message;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CountingUpdater implements Updater {
  @Getter @Setter private PollingAtomicLong connectCount = new PollingAtomicLong(0);
  @Getter @Setter private PollingAtomicLong disconnectCount = new PollingAtomicLong(0);
  @Getter @Setter private PollingAtomicLong readyCount = new PollingAtomicLong(0);
  @Getter @Setter private PollingAtomicLong failureCount = new PollingAtomicLong(0);
  @Getter @Setter private PollingAtomicLong updateCount = new PollingAtomicLong(0);

  @Override
  public void onConnected() {
    log.debug("onConnected");
    connectCount.incrementAndGet();
  }

  @Override
  public void onDisconnected(String reason) {
    log.debug("onDisconnected" + reason);
    disconnectCount.incrementAndGet();
  }

  @Override
  public void onReady() {
    log.debug("onReady");
    readyCount.incrementAndGet();
  }

  @Override
  public void onFailure(String message) {
    log.debug("onFailure: " + message);
    failureCount.incrementAndGet();
  }

  @Override
  public void update(Message message) {
    log.debug("update: " + message);
    updateCount.incrementAndGet();
  }
};
