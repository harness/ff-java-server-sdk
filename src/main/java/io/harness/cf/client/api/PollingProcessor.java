package io.harness.cf.client.api;

import static io.harness.cf.client.common.Utils.shutdownExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class PollingProcessor {

  private final Connector connector;
  private final int pollIntervalSeconds;
  private final StorageRepository repository;
  private final AtomicBoolean initialized = new AtomicBoolean();
  private final PollerCallback callback;
  private ScheduledFuture<?> runningTask = null;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public PollingProcessor(
      @NonNull final Connector connector,
      @NonNull final StorageRepository repository,
      final int pollIntervalSeconds,
      @NonNull final PollerCallback callback) {
    this.connector = connector;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.repository = repository;
    this.callback = callback;
  }

  public boolean retrieveAll() {
    try {
      refresh();
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ConnectorException | RuntimeException ex) {
      log.warn("retrieveAll failed: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
      return false;
    }
  }

  private void refresh() throws ConnectorException, InterruptedException {
    final List<Runnable> notifications;
    // Serialize requests with stream writes without blocking reads of the current cache.
    Lock lock = repository.snapshotLock();
    lock.lockInterruptibly();
    try {
      ConfigurationSnapshot snapshot =
          new ConfigurationSnapshot(connector.getFlags(), connector.getSegments());
      notifications = repository.replaceSnapshot(snapshot);
    } finally {
      lock.unlock();
    }
    StorageRepository.notifyCallbacks(notifications);
    if (initialized.compareAndSet(false, true)) {
      log.debug("PollingProcessor initialized");
      callback.onPollerReady();
    }
  }

  private void runOneIteration() {
    Thread.currentThread().setName("PollThread");
    log.debug("running poll iteration");
    try {
      refresh();
    } catch (Exception exc) {
      if (exc instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      if (!initialized.get()) {
        callback.onPollerFailed(exc);
      } else {
        callback.onPollerError(exc);
      }
    }
  }

  public void start() {
    if (isRunning()) {
      return;
    }

    runningTask =
        scheduler.scheduleAtFixedRate(this::runOneIteration, 0, pollIntervalSeconds, SECONDS);
    SdkCodes.infoPollStarted(pollIntervalSeconds);
  }

  public void stop() {
    log.debug("Stopping PollingProcessor");

    if (scheduler.isShutdown()) {
      return;
    }

    if (runningTask == null) {
      return;
    }

    runningTask.cancel(false);
    runningTask = null;
  }

  public void close() {
    stop();

    shutdownExecutorService(
        scheduler,
        SdkCodes::infoPollingStopped,
        errMsg -> log.warn("failed to stop polling scheduler: {}", errMsg));

    log.debug("Closing PollingProcessor");
  }

  public boolean isRunning() {
    return runningTask != null && !runningTask.isCancelled();
  }
}
