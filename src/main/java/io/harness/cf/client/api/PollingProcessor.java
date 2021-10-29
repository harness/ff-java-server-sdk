package io.harness.cf.client.api;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.client.common.Repository;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.StringUtils;

@Slf4j
public class PollingProcessor extends AbstractScheduledService {

  public enum Event {
    READY,
    ERROR
  }

  @Setter private String environment;
  @Setter private String cluster;

  private final ClientApi api;
  private final int pollIntervalSeconds;
  private final Repository repository;
  private boolean initialized = false;
  private final EventBus eventBus;

  public PollingProcessor(
      ClientApi api, EventBus eventBus, Repository repository, int pollIntervalSeconds) {
    this.api = api;
    this.pollIntervalSeconds = pollIntervalSeconds;
    this.repository = repository;
    this.eventBus = eventBus;
  }

  public CompletableFuture<List<FeatureConfig>> retrieveFlags() {
    CompletableFuture<List<FeatureConfig>> completableFuture = new CompletableFuture<>();
    try {
      log.debug("Fetching flags started");
      List<FeatureConfig> featureConfig = this.api.getFeatureConfig(this.environment, this.cluster);
      log.debug("Fetching flags finished");
      featureConfig.forEach(fc -> repository.setFlag(fc.getFeature(), fc));
      completableFuture.complete(featureConfig);
    } catch (ApiException e) {
      log.error("Error loading flags, err: {}", e.getMessage());
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  public CompletableFuture<List<Segment>> retrieveSegments() {
    CompletableFuture<List<Segment>> completableFuture = new CompletableFuture<>();
    try {
      log.info("Fetching segments started");
      List<Segment> segments = this.api.getAllSegments(this.environment, this.cluster);
      log.info("Fetching segments finished");
      segments.forEach(s -> repository.setSegment(s.getIdentifier(), s));
      completableFuture.complete(segments);
    } catch (ApiException e) {
      log.error("Error loading segments, err: {}", e.getMessage());
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }

  @Override
  protected void runOneIteration() {
    if (StringUtils.isBlank(environment) && StringUtils.isBlank(cluster)) {
      log.info("Environment or cluster is missing");
      return;
    }
    log.info("running poll iteration");
    try {
      CompletableFuture.allOf(retrieveFlags(), retrieveSegments()).join();
      if (!initialized) {
        initialized = true;
        eventBus.post(new CustomEvent<>(Event.READY));
      }
    } catch (CompletionException exc) {
      log.error("Error polling the data, err: {}", exc.getMessage());
      eventBus.post(new CustomEvent<>(Event.ERROR, exc.getMessage()));
    }
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    // first argument is for initial delay so this should be always 0
    return Scheduler.newFixedDelaySchedule(0, pollIntervalSeconds, TimeUnit.SECONDS);
  }

  public void close() {
    stopAsync();
  }
}
