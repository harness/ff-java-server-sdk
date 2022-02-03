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
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateProcessor implements AutoCloseable {
  private final Connector connector;
  private final Repository repository;
  private final Updater updater;
  private final ExecutorService executor = Executors.newFixedThreadPool(100);

  private Service stream;

  public UpdateProcessor(
      @NonNull final Connector connector,
      @NonNull final Repository repository,
      @NonNull final Updater callback) {
    this.connector = connector;
    this.repository = repository;
    this.updater = callback;
    log.info("Update processor initialized");
  }

  public void start() {
    log.info("Starting updater (EventSource)");
    try {
      stream = connector.stream(this.updater);
      stream.start();
    } catch (ConnectorException | InterruptedException e) {
      log.error("Starting updater failed with exc: {}", e.getMessage());
    }
  }

  public void stop() {
    try {
      if (stream != null) {
        log.info("Stopping updater (EventSource)");
        stream.stop();
      }
      boolean result = executor.awaitTermination(3, TimeUnit.SECONDS);
      if (result) {
        log.debug("All tasks done");
      } else {
        log.warn("UpdateProcessor: timeout while wait threads to finish!");
      }
    } catch (InterruptedException e) {
      log.error("Exception was raised when stopping update tasks", e);
    }
    executor.shutdown();
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
        final FeatureConfig config = connector.getFlag(message.getIdentifier());
        if (config != null) {
          if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
            repository.setFlag(message.getIdentifier(), config);
            log.debug("Set new segment with key {} and value {}", message.getIdentifier(), config);
          } else if (message.getEvent().equals("delete")) {
            log.debug("Delete flag with key {}", message.getIdentifier());
            repository.deleteFlag(message.getIdentifier());
          }
        }
      } catch (ConnectorException e) {
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
        final Segment segment = connector.getSegment(message.getIdentifier());
        if (segment != null) {
          if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
            log.debug("Set new segment with key {} and value {}", message.getIdentifier(), segment);
            repository.setSegment(message.getIdentifier(), segment);
          } else if (message.getEvent().equals("delete")) {
            log.debug("Delete segment with key {}", message.getIdentifier());
            repository.deleteSegment(message.getIdentifier());
          }
        }
      } catch (ConnectorException e) {
        log.error(
            "Exception was raised when fetching segment '{}' with the message {}",
            message.getIdentifier(),
            e.getMessage());
      }
    };
  }

  @Override
  public void close() {
    log.info("Closing UpdateProcessor");
    stop();
    if (stream != null) {
      try {
        stream.close();
      } catch (InterruptedException e) {
        log.error("Exception was raised while trying to close the stream, err: {}", e.getMessage());
      }
    }
    log.info("UpdateProcessor closed");
  }
}
