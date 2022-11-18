package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.MoreExecutors;
import io.harness.cf.client.common.ScheduledServiceStateLogger;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class PollingProcessor extends AbstractScheduledService {

  private final Connector connector;
  private final int pollIntervalSeconds;
  private final Repository repository;
  private boolean initialized = false;
  private final PollerCallback callback;

  public PollingProcessor(
      @NonNull final Connector connector,
      @NonNull final Repository repository,
      final int pollIntervalSeconds,
      @NonNull final PollerCallback callback) {
    this.connector = connector;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.repository = repository;
    this.callback = callback;
    this.addListener(
        new ScheduledServiceStateLogger(PollingProcessor.class.getSimpleName()),
        MoreExecutors.directExecutor());
  }

  public CompletableFuture<List<FeatureConfig>> retrieveFlags() {
    CompletableFuture<List<FeatureConfig>> completableFuture = new CompletableFuture<>();
    try {
      log.debug("Fetching flags started");
      final List<FeatureConfig> featureConfig = connector.getFlags();
      log.debug("Fetching flags finished");
      featureConfig.forEach(
          fc -> {
            if (fc != null) {
              repository.setFlag(fc.getFeature(), fc);
            }
          });
      completableFuture.complete(featureConfig);
    } catch (ConnectorException e) {
      log.error(
          "Exception was raised when fetching flags data with the message {}", e.getMessage());
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  public CompletableFuture<List<Segment>> retrieveSegments() {
    final CompletableFuture<List<Segment>> completableFuture = new CompletableFuture<>();
    try {
      log.debug("Fetching segments started");
      final List<Segment> segments = connector.getSegments();
      log.debug("Fetching segments finished");
      segments.forEach(
          s -> {
            if (s != null) {
              repository.setSegment(s.getIdentifier(), s);
            }
          });
      completableFuture.complete(segments);
    } catch (ConnectorException e) {
      log.error(
          "Exception was raised when fetching flags data with the message {}", e.getMessage());
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  @Override
  protected void runOneIteration() {
    log.debug("running poll iteration");
    try {
      CompletableFuture.allOf(retrieveFlags(), retrieveSegments()).join();
      if (!initialized) {
        initialized = true;
        log.info("PollingProcessor initialized");
        callback.onPollerReady();
      }
    } catch (Exception exc) {
      if (!initialized) {
        callback.onPollerFailed(exc);
      } else {
        callback.onPollerError(exc);
      }
    }
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    // first argument is for initial delay so this should be always 0
    return Scheduler.newFixedDelaySchedule(0, pollIntervalSeconds, TimeUnit.SECONDS);
  }

  public void start() {
    if (isRunning()) {
      return;
    }
    log.info("Starting PollingProcessor with request interval: {}", pollIntervalSeconds);
    startAsync();
  }

  public void stop() {
    log.info("Stopping PollingProcessor");
    if (isRunning()) {
      stopAsync();
      log.info("PollingProcessor stopped");
    }
  }

  public void close() {
    stop();
    log.info("Closing PollingProcessor");
  }
}
