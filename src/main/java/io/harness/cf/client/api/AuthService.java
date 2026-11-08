package io.harness.cf.client.api;

import static io.harness.cf.client.common.Utils.shutdownExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class AuthService {

  private final Connector connector;
  private final int pollIntervalInSec;
  private final AuthCallback callback;
  private volatile Thread workerThread;
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          1,
          task -> {
            Thread worker = new Thread(task, "AuthThread");
            workerThread = worker;
            return worker;
          });
  private ScheduledFuture<?> runningTask = null;
  private volatile boolean closed;

  public AuthService(
      @NonNull final Connector connector,
      final int pollIntervalInSec,
      @NonNull final AuthCallback callback) {

    this.connector = connector;
    this.pollIntervalInSec = pollIntervalInSec;
    this.callback = callback;
    log.debug("Authentication service initialized");
  }

  private void runOneIteration() {
    if (closed) return;
    Thread.currentThread().setName("AuthThread");
    try {
      connector.authenticate();
      SdkCodes.infoSdkAuthOk();
      callback.onAuthSuccess();
      log.debug("Stopping Auth service");
    } catch (ConnectorException e) {
      if (closed) return;
      log.error("Exception while authenticating", e);
      callback.onFailure(e.getMessage());
    } finally {
      stop();
    }
  }

  public synchronized void start() {
    if (closed || isRunning()) {
      log.debug("authentication already in progress, skipping");
      return;
    }
    log.debug("authentication started");
    this.runningTask =
        scheduler.scheduleAtFixedRate(this::runOneIteration, 0, pollIntervalInSec, SECONDS);
  }

  public synchronized void stop() {
    if (scheduler.isShutdown()) {
      return;
    }

    if (runningTask == null) {
      return;
    }

    runningTask.cancel(false);
    runningTask = null;
    log.debug("authentication job done");
  }

  public void close() {
    synchronized (this) {
      if (closed) return;
      closed = true;
      stop();
    }
    if (Thread.currentThread() == workerThread) {
      scheduler.shutdown();
      return;
    }
    scheduler.shutdownNow();

    shutdownExecutorService(
        scheduler,
        SdkCodes::infoPollingStopped,
        errMsg -> log.warn("failed to shutdown auth scheduler: {}", errMsg));

    log.debug("Authentication service closed");
  }

  public synchronized boolean isRunning() {
    return runningTask != null && !runningTask.isDone();
  }
}
