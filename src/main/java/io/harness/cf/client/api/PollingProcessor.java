package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
      Connector connector,
      Repository repository,
      int pollIntervalSeconds,
      PollerCallback callback) {
    this.connector = connector;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.repository = repository;
    this.callback = callback;
  }

  public CompletableFuture<List<FeatureConfig>> retrieveFlags() {
    CompletableFuture<List<FeatureConfig>> completableFuture = new CompletableFuture<>();

    log.debug("Fetching flags started");
    List<FeatureConfig> featureConfig = connector.getFlags();
    log.debug("Fetching flags finished");
    featureConfig.forEach(fc -> repository.setFlag(fc.getFeature(), fc));
    completableFuture.complete(featureConfig);

    return completableFuture;
  }

  public CompletableFuture<List<Segment>> retrieveSegments() {
    CompletableFuture<List<Segment>> completableFuture = new CompletableFuture<>();
    log.debug("Fetching segments started");
    List<Segment> segments = connector.getSegments();
    log.debug("Fetching segments finished");
    segments.forEach(s -> repository.setSegment(s.getIdentifier(), s));
    completableFuture.complete(segments);
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
    } catch (CompletionException exc) {
      log.error("Error polling the data, err: {}", exc.getMessage());
      callback.onPollerError(exc.getMessage());
    }
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    // first argument is for initial delay so this should be always 0
    return Scheduler.newFixedDelaySchedule(0, pollIntervalSeconds, TimeUnit.SECONDS);
  }

  public void start() {
    log.info("Starting PollingProcessor with request interval: {}", pollIntervalSeconds);
    startAsync();
  }

  public void stop() {
    log.info("Stopping PollingProcessor");
    stopAsync();
  }

  public void close() {
    stop();
    log.info("Closing PollingProcessor");
  }
}
