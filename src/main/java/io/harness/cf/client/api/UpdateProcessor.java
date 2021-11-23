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

  public UpdateProcessor(Connector connector, Repository repository, Updater callback) {
    this.connector = connector;
    this.repository = repository;
    this.updater = callback;
  }

  public void start() {
    log.debug("Starting updater (stream)");
    try {
      stream = connector.stream(this.updater);
      stream.start();
    } catch (ConnectorException e) {
      log.error("Starting updater failed with exc: {}", e.getMessage());
    }
  }

  public void stop() {
    if (stream != null) {
      stream.stop();
    }
    try {
      boolean result = executor.awaitTermination(3, TimeUnit.SECONDS);
      if (result) {
        log.debug("All tasks done");
      } else {
        log.warn("UpdateProcessor: timeout while wait threads to finish!");
      }
    } catch (InterruptedException e) {
      log.error(
          "Exception was raised when stopping update tasks with the message {}", e.getMessage());
    }
    executor.shutdown();
  }

  public void update(@NonNull Message message) {
    if (message.getDomain().equals("flag")) {
      executor.submit(processFlag(message));
    }

    if (message.getDomain().equals("target-segment")) {
      executor.submit(processSegment(message));
    }
  }

  protected Runnable processFlag(@NonNull Message message) {

    return () -> {
      try {
        FeatureConfig config = connector.getFlag(message.getIdentifier());
        if (config != null) {
          if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
            repository.setFlag(message.getIdentifier(), config);
          } else if (message.getEvent().equals("delete")) {
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

  protected Runnable processSegment(@NonNull Message message) {
    return () -> {
      try {
        Segment segment = connector.getSegment(message.getIdentifier());
        if (segment != null) {
          if (message.getEvent().equals("create") || message.getEvent().equals("patch")) {
            repository.setSegment(message.getIdentifier(), segment);
          } else if (message.getEvent().equals("delete")) {
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
  }
}
