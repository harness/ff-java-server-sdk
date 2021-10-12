package io.harness.cf.client.api;


import com.github.benmanes.caffeine.cache.Cache;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class SSEListener implements ServerSentEvent.Listener {

  private final Gson gson = new Gson();
  private final ClientApi defaultApi;
  private final Cache<String, FeatureConfig> featureCache;
  private final Cache<String, Segment> segmentCache;
  private final String environmentID;
  private final String cluster;
  private final CfClient cfClient;

  SSEListener(
      ClientApi defaultApi,
      Cache<String, FeatureConfig> featureCache,
      Cache<String, Segment> segmentCache,
      String environmentID,
      String cluster,
      CfClient cfClient) {

    this.defaultApi = defaultApi;
    this.featureCache = featureCache;
    this.segmentCache = segmentCache;
    this.environmentID = environmentID;
    this.cluster = cluster;
    this.cfClient = cfClient;
  }

  @Override
  public void onOpen(ServerSentEvent serverSentEvent, Response response) {

    log.info("SSE connection opened. ");
    cfClient.stopPoller();
  }

  @Override
  public void onMessage(ServerSentEvent serverSentEvent, String s, String s1, String s2) {

    JsonObject jsonObject;
    try {
      jsonObject = gson.fromJson(s1, JsonObject.class);
    } catch (JsonSyntaxException ex) {
      jsonObject = gson.fromJson(s2, JsonObject.class);
    }

    String domain = jsonObject.get("domain").getAsString();
    if (domain.equals("flag")) {
      processFeature(jsonObject);
    } else if (domain.equals("target-segment")) {
      processSegment(jsonObject);
    }
  }

  private void processFeature(JsonObject jsonObject) {

    log.info("Syncing the latest features..");
    String identifier = jsonObject.get("identifier").getAsString();
    Long version = jsonObject.get("version").getAsLong();

    for (int i = 0; i < 3; i++) {
      try {

        FeatureConfig featureConfig =
            defaultApi.getFeatureConfigByIdentifier(identifier, environmentID, cluster);
        if (version.equals(featureConfig.getVersion())) {
          featureCache.put(featureConfig.getFeature(), featureConfig);
          break;
        }
      } catch (ApiException e) {

        log.error("Failed to sync the feature {} due to {}", identifier, e.getMessage());
      }
    }
  }

  private void processSegment(JsonObject jsonObject) {

    log.info("Syncing the latest segments..");
    String identifier = jsonObject.get("identifier").getAsString();
    // Long version = jsonObject.get("version").getAsLong();
    try {

      List<Segment> segments = defaultApi.getAllSegments(environmentID, cluster);
      if (segments != null) {
        segmentCache.putAll(
            segments.stream()
                .collect(Collectors.toMap(Segment::getIdentifier, segment -> segment)));
      }
    } catch (ApiException e) {
      log.error("Failed to sync the segment {} due to {}", identifier, e.getMessage());
    }
  }

  @Override
  public void onComment(ServerSentEvent serverSentEvent, String s) {

    log.info("On comment");
  }

  @Override
  public boolean onRetryTime(ServerSentEvent serverSentEvent, long l) {

    return false;
  }

  @Override
  public boolean onRetryError(
      ServerSentEvent serverSentEvent, Throwable throwable, Response response) {
    return false;
  }

  @Override
  public void onClosed(ServerSentEvent serverSentEvent) {

    log.info("SSE connection closed. Switching to polling mode.");
    cfClient.startPollingMode();
  }

  @Override
  public Request onPreRetry(ServerSentEvent serverSentEvent, Request request) {

    return null;
  }
}
