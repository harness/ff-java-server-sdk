package io.harness.cf.client.api;

import static io.harness.cf.client.common.Utils.shutdownExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import java.util.ArrayList;
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
  private final AtomicBoolean closed = new AtomicBoolean();
  private final PollerCallback callback;
  private ScheduledFuture<?> runningTask = null;

  private volatile Thread workerThread;
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          1,
          task -> {
            Thread worker = new Thread(task, "PollThread");
            workerThread = worker;
            return worker;
          });

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
    if (closed.get()) return false;
    try {
      refresh();
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ConnectorException | RuntimeException ex) {
      if (closed.get()) return false;
      log.warn("retrieveAll failed: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
      return false;
    }
  }

  private void refresh() throws ConnectorException, InterruptedException {
    final List<Runnable> notifications = new ArrayList<>();
    // Serialize requests with stream writes without blocking reads of the current cache.
    Lock lock = repository.snapshotLock();
    lock.lockInterruptibly();
    try {
      if (closed.get()) throw new CancellationException("Poller closed");
      long sequence = repository.currentStreamSequence();
      ConfigurationSnapshot snapshot =
          new ConfigurationSnapshot(connector.getFlags(), connector.getSegments());
      if (closed.get()) throw new CancellationException("Poller closed");
      repository.replaceSnapshot(snapshot, notifications);
      repository.markSnapshotApplied(sequence);
    } finally {
      lock.unlock();
      StorageRepository.notifyCallbacks(notifications);
    }
    if (initialized.compareAndSet(false, true)) {
      log.debug("PollingProcessor initialized");
      callback.onPollerReady();
    }
    callback.onPollerRefresh();
  }

  private void runOneIteration() {
    if (closed.get()) return;
    Thread.currentThread().setName("PollThread");
    log.debug("running poll iteration");
    try {
      refresh();
    } catch (Exception exc) {
      if (closed.get()) return;
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

  public synchronized void start() {
    if (closed.get() || isRunning()) {
      return;
    }

    runningTask =
        scheduler.scheduleAtFixedRate(this::runOneIteration, 0, pollIntervalSeconds, SECONDS);
    SdkCodes.infoPollStarted(pollIntervalSeconds);
  }

  public synchronized void stop() {
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
    if (!closed.compareAndSet(false, true)) return;
    stop();
    if (Thread.currentThread() == workerThread) {
      scheduler.shutdown();
      return;
    }
    scheduler.shutdownNow();

    shutdownExecutorService(
        scheduler,
        SdkCodes::infoPollingStopped,
        errMsg -> log.warn("failed to stop polling scheduler: {}", errMsg));

    log.debug("Closing PollingProcessor");
  }

  public synchronized boolean isRunning() {
    return runningTask != null && !runningTask.isDone();
  }
}
