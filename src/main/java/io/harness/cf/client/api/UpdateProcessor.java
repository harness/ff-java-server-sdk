package io.harness.cf.client.api;

import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.connector.Service;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.client.dto.Message;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class UpdateProcessor implements AutoCloseable {
  private final Connector connector;
  private final Repository repository;
  private final Updater updater;
  private final ExecutorService executor = Executors.newFixedThreadPool(10);

  private Service stream;

  private boolean running = false;

  public UpdateProcessor(
      @NonNull final Connector connector,
      @NonNull final Repository repository,
      @NonNull final Updater callback) {
    this.connector = connector;
    this.repository = repository;
    this.updater = callback;
    log.debug("Update processor initialized");
  }

  public void start() {
    log.debug("Starting updater (EventSource)");
    if (running) {
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

  public void stop() {
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

    if (message.getDomain().equals("flag")) {
      log.debug("execute processFlag with message {}", message);
      executor.submit(processFlag(message));
    }

    if (message.getDomain().equals("target-segment")) {
      log.debug("execute processSegment with message {}", message);
      executor.submit(processSegment(message));
    }
  }

  protected Runnable processFlag(@NonNull final Message message) {

    return () -> {
      try {
        if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
          final FeatureConfig config = connector.getFlag(message.getIdentifier());
          if (config != null) {
            repository.setFlag(message.getIdentifier(), config);
            log.trace("Set new segment with key {} and value {}", message.getIdentifier(), config);
          }
        } else if (message.getEvent().equals("delete")) {
          log.debug("Delete flag with key {}", message.getIdentifier());
          repository.deleteFlag(message.getIdentifier());
        }
      } catch (Throwable e) {
        log.error(
            "Exception was raised when fetching flag '{}' with the message {}",
            message.getIdentifier(),
            e.getMessage());
      }
    };
  }

  protected Runnable processSegment(@NonNull final Message message) {
    return () -> {
      try {
        if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
          final Segment segment = connector.getSegment(message.getIdentifier());
          if (segment != null) {
            log.trace("Set new segment with key {} and value {}", message.getIdentifier(), segment);
            repository.setSegment(message.getIdentifier(), segment);
          }
        } else if (message.getEvent().equals("delete")) {
          log.debug("Delete segment with key {}", message.getIdentifier());
          repository.deleteSegment(message.getIdentifier());
        }
      } catch (Throwable e) {
        log.error(
            "Exception was raised when fetching segment '{}' with the message {}",
            message.getIdentifier(),
            e.getMessage());
      }
    };
  }

  @Override
  public void close() {
    log.debug("Closing UpdateProcessor");
    stop();
    if (stream != null) {
      try {
        stream.close();
      } catch (InterruptedException e) {
        log.error("Exception was raised while trying to close the stream, err: {}", e.getMessage());
        Thread.currentThread().interrupt();
      }
    }

    executor.shutdownNow();
    log.debug("UpdateProcessor closed");
  }

  public void restart() {
    log.debug("Restart SSE stream");
    stop();
    start();
  }
}
