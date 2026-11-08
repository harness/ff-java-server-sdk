package io.harness.cf.client.api;

import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.connector.Service;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.client.dto.Message;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class UpdateProcessor implements AutoCloseable {
  private static final int MAX_PENDING_UPDATES = 256;
  private static final long ADMISSION_CHECK_MILLIS = 100;
  private final Connector connector;
  private final Repository repository;
  private final Lock updateLock;
  private final Updater updater;
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile Thread workerThread;
  private final ThreadPoolExecutor executor =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(MAX_PENDING_UPDATES),
          task -> {
            Thread worker = new Thread(task, "HarnessFlagUpdates");
            workerThread = worker;
            return worker;
          },
          this::awaitCapacity);

  private Service stream;

  private boolean running = false;

  public UpdateProcessor(
      @NonNull final Connector connector,
      @NonNull final Repository repository,
      @NonNull final Updater callback) {
    this.connector = connector;
    this.repository = repository;
    this.updateLock =
        repository instanceof StorageRepository
            ? ((StorageRepository) repository).updateLock()
            : new ReentrantLock();
    this.updater = callback;
    log.debug("Update processor initialized");
  }

  public synchronized void start() {
    log.debug("Starting updater (EventSource)");
    if (closed.get() || running) {
      log.debug("updater already started");
      return;
    }

    try {
      if (stream != null) {
        stream.close();
      }
      stream = connector.stream(this.updater);
      stream.start();
      running = true;
    } catch (ConnectorException | InterruptedException e) {
      log.error("Starting updater failed with exc: {}", e.getMessage());

      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public synchronized void stop() {
    try {
      if (stream != null) {
        if (!running) {
          log.debug("updater cannot be stopped because it is not in running state");
          return;
        }
        log.debug("Stopping updater (EventSource)");
        stream.stop();
        running = false;
      }

    } catch (InterruptedException e) {
      log.error("Exception was raised when stopping update tasks", e);
      Thread.currentThread().interrupt();
    }
  }

  public void update(@NonNull final Message message) {
    if (closed.get()) throw new RejectedExecutionException("Update processor is closed");

    if (message.getDomain().equals("flag")) {
      log.debug("execute processFlag with message {}", message);
      executor.execute(processFlag(message));
    }

    if (message.getDomain().equals("target-segment")) {
      log.debug("execute processSegment with message {}", message);
      executor.execute(processSegment(message));
    }
  }

  private void awaitCapacity(Runnable task, ThreadPoolExecutor pool) {
    if (Thread.currentThread() == workerThread) {
      throw new RejectedExecutionException("The update callback cannot wait for its own queue");
    }
    try {
      while (!pool.isShutdown() && !closed.get()) {
        if (pool.getQueue().offer(task, ADMISSION_CHECK_MILLIS, TimeUnit.MILLISECONDS)) {
          if ((pool.isShutdown() || closed.get()) && pool.remove(task)) break;
          return;
        }
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RejectedExecutionException("Interrupted while waiting for update capacity", ex);
    }
    throw new RejectedExecutionException("Update processor is closed");
  }

  protected Runnable processFlag(@NonNull final Message message) {
    long sequence = nextStreamSequence();
    return () -> {
      List<Runnable> notifications = new ArrayList<>();
      try {
        updateLock.lockInterruptibly();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
      try {
        if (closed.get() || isSupersededBySnapshot(sequence)) return;
        if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
          final FeatureConfig config = connector.getFlag(message.getIdentifier());
          if (config != null) {
            updateRepository(
                () -> repository.setFlag(message.getIdentifier(), config), notifications);
            log.trace("Set new segment with key {} and value {}", message.getIdentifier(), config);
          }
        } else if (message.getEvent().equals("delete")) {
          log.debug("Delete flag with key {}", message.getIdentifier());
          updateRepository(() -> repository.deleteFlag(message.getIdentifier()), notifications);
        }
      } catch (ConnectorException | RuntimeException e) {
        log.error(
            "Exception was raised when fetching flag '{}' with the message {}",
            message.getIdentifier(),
            e.getMessage());
      } finally {
        updateLock.unlock();
      }
      StorageRepository.notifyCallbacks(notifications);
    };
  }

  protected Runnable processSegment(@NonNull final Message message) {
    long sequence = nextStreamSequence();
    return () -> {
      List<Runnable> notifications = new ArrayList<>();
      try {
        updateLock.lockInterruptibly();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return;
      }
      try {
        if (closed.get() || isSupersededBySnapshot(sequence)) return;
        if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
          final Segment segment = connector.getSegment(message.getIdentifier());
          if (segment != null) {
            log.trace("Set new segment with key {} and value {}", message.getIdentifier(), segment);
            updateRepository(
                () -> repository.setSegment(message.getIdentifier(), segment), notifications);
          }
        } else if (message.getEvent().equals("delete")) {
          log.debug("Delete segment with key {}", message.getIdentifier());
          updateRepository(() -> repository.deleteSegment(message.getIdentifier()), notifications);
        }
      } catch (ConnectorException | RuntimeException e) {
        log.error(
            "Exception was raised when fetching segment '{}' with the message {}",
            message.getIdentifier(),
            e.getMessage());
      } finally {
        updateLock.unlock();
      }
      StorageRepository.notifyCallbacks(notifications);
    };
  }

  private void updateRepository(Runnable mutation, List<Runnable> notifications) {
    if (repository instanceof StorageRepository) {
      ((StorageRepository) repository).collectNotifications(mutation, notifications);
    } else {
      mutation.run();
    }
  }

  private long nextStreamSequence() {
    return repository instanceof StorageRepository
        ? ((StorageRepository) repository).nextStreamSequence()
        : 0;
  }

  private boolean isSupersededBySnapshot(long sequence) {
    return repository instanceof StorageRepository
        && ((StorageRepository) repository).isSupersededBySnapshot(sequence);
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    log.debug("Closing UpdateProcessor");
    try {
      try {
        stop();
      } finally {
        if (stream != null) {
          try {
            stream.close();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing the update stream", ex);
          }
        }
      }
    } finally {
      if (Thread.currentThread() == workerThread) {
        executor.getQueue().clear();
        executor.shutdown();
      } else {
        executor.shutdownNow();
        io.harness.cf.client.common.Utils.shutdownExecutorService(
            executor,
            () -> log.debug("Update worker stopped"),
            message -> log.warn("Failed to stop update worker: {}", message));
      }
    }
    log.debug("UpdateProcessor closed");
  }

  public void restart() {
    log.debug("Restart SSE stream");
    stop();
    start();
  }
}
