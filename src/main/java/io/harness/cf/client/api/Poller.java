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

  private final DefaultApi defaultApi;
  private final Cache<String, FeatureConfig> featureCache;
  private final Cache<String, Segment> segmentCache;
  private String environmentID;
  private String clusterIdentifier;
  private int pollIntervalInSec;
  private boolean streamEnabled;
  private CfClient cfClient;

  public Poller(
      DefaultApi defaultApi,
      Cache<String, FeatureConfig> featureCache,
      Cache<String, Segment> segmentCache,
      String environmentID,
      String clusterIdentifier,
      int pollIntervalInSec,
      boolean streamEnabled,
      CfClient cfClient) {
    this.defaultApi = defaultApi;
    this.featureCache = featureCache;
    this.segmentCache = segmentCache;
    this.environmentID = environmentID;
    this.clusterIdentifier = clusterIdentifier;
    this.pollIntervalInSec = pollIntervalInSec;
    this.streamEnabled = streamEnabled;
    this.cfClient = cfClient;
  }

  @Override
  protected void runOneIteration() throws Exception {
    try {
      log.debug("Getting the latest features and segments..");
      List<FeatureConfig> featureConfigs =
          defaultApi.getFeatureConfig(environmentID, clusterIdentifier);
      if (featureConfigs != null) {
        featureCache.putAll(
            featureConfigs.stream()
                .collect(Collectors.toMap(FeatureConfig::getFeature, config -> config)));
      }

      List<Segment> segments = defaultApi.getAllSegments(environmentID, clusterIdentifier);
      if (segments != null) {
        segmentCache.putAll(
            segments.stream()
                .collect(Collectors.toMap(Segment::getIdentifier, segment -> segment)));
      }

      if (streamEnabled) {
        log.info("Switching to streaming mode.");
        cfClient.startSSE();
      }
    } catch (Exception e) {
      log.error("Failed to get FeatureConfig or Segments: {}", e.getMessage());
    }
  }

  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(0L, pollIntervalInSec, TimeUnit.SECONDS);
  }
}
