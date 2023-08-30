package io.harness.cf.client.api;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class PollingProcessor {

  private final Connector connector;
  private final int pollIntervalSeconds;
  private final Repository repository;
  private boolean initialized = false;
  private final PollerCallback callback;
  private boolean isRunning = false;

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public PollingProcessor(
      @NonNull final Connector connector,
      @NonNull final Repository repository,
      final int pollIntervalSeconds,
      @NonNull final PollerCallback callback) {
    this.connector = connector;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.repository = repository;
    this.callback = callback;
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
    } catch (Throwable e) {
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
    } catch (Throwable e) {
      log.error(
          "Exception was raised when fetching flags data with the message {}", e.getMessage(), e);
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  public void retrieveAll() {
    CompletableFuture.allOf(retrieveFlags(), retrieveSegments()).join();
  }

  private void runOneIteration() {
    log.debug("running poll iteration");
    try {
      retrieveAll();
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

  public void start() {
    if (isRunning()) {
      return;
    }

    scheduler.scheduleAtFixedRate(this::runOneIteration, 0, pollIntervalSeconds, SECONDS);
    SdkCodes.infoPollStarted(pollIntervalSeconds);
    isRunning = true;
  }

  public void stop() {
    log.info("Stopping PollingProcessor");

    if (scheduler.isShutdown()) {
      return;
    }

    isRunning = false;
    scheduler.shutdown();

    try {
      if (!scheduler.awaitTermination(10, SECONDS)) {
        scheduler.shutdownNow();
        if (scheduler.awaitTermination(10, SECONDS)) {
          SdkCodes.infoPollingStopped();
        } else {
          log.warn("Polling thread pool did not terminate");
        }
      } else {
        SdkCodes.infoPollingStopped();
      }
    } catch (InterruptedException ie) {
      scheduler.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  public void close() {
    stop();
    log.info("Closing PollingProcessor");
  }

  public boolean isRunning() {
    return isRunning;
  }
}
