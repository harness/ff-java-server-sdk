package io.harness.cf.client.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.api.DefaultApi;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Poller extends AbstractScheduledService {

  private final String cluster;
  private final CfClient cfClient;
  private final String environmentID;
  private final int pollIntervalInSec;
  private final DefaultApi defaultApi;
  private final boolean streamEnabled;
  private final Cache<String, Segment> segmentCache;
  private final Cache<String, FeatureConfig> featureCache;

  public Poller(
      DefaultApi defaultApi,
      Cache<String, FeatureConfig> featureCache,
      Cache<String, Segment> segmentCache,
      String environmentID,
      String cluster,
      int pollIntervalInSec,
      boolean streamEnabled,
      CfClient cfClient) {

    this.cluster = cluster;
    this.cfClient = cfClient;
    this.defaultApi = defaultApi;
    this.featureCache = featureCache;
    this.segmentCache = segmentCache;
    this.environmentID = environmentID;
    this.streamEnabled = streamEnabled;
    this.pollIntervalInSec = pollIntervalInSec;
  }

  @Override
  protected void runOneIteration() {

    if (Thread.currentThread().isInterrupted()) {

      return;
    }
    try {
      log.debug("Getting the latest features and segments..");
      List<FeatureConfig> featureConfigs = defaultApi.getFeatureConfig(environmentID, cluster);

      if (featureConfigs != null) {
        featureCache.putAll(
            featureConfigs.stream()
                .collect(Collectors.toMap(FeatureConfig::getFeature, config -> config)));
      }

      List<Segment> segments = defaultApi.getAllSegments(environmentID, cluster);
      if (segments != null) {
        segmentCache.putAll(
            segments.stream()
                .collect(Collectors.toMap(Segment::getIdentifier, segment -> segment)));
      }
    } catch (Exception e) {
      log.error("Failed to get FeatureConfig or Segments: {}", e.getMessage());
    } finally {
      if (streamEnabled) {

        log.info("Switching to streaming mode.");
        cfClient.startSSE();
      }
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(pollIntervalInSec, pollIntervalInSec, TimeUnit.SECONDS);
  }
}
