package io.harness.cf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.ApiCallback;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
class StreamProcessor implements ServerSentEvent.Listener {
  private final Gson gson = new Gson();
  private final ClientApi clientApi;
  private final Repository repository;
  private final StreamCallback callback;
  @Setter private String environmentID;
  @Setter private String cluster;
  @Setter private String token;

  StreamProcessor(
      String sdkKey,
      ClientApi clientApi,
      Repository repository,
      String url,
      @NonNull StreamCallback callback) {

    this.clientApi = clientApi;
    this.repository = repository;
    this.callback = callback;

    final String sseUrl = String.join("", url, "/stream?cluster=" + cluster);

    Request authorization =
        new Request.Builder()
            .url(String.format(sseUrl, environmentID))
            .header("Authorization", "Bearer " + token)
            .header("API-Key", sdkKey)
            .build();
  }

  public void start() {}

  @Override
  public void onOpen(ServerSentEvent serverSentEvent, Response response) {

    log.info("SSE connection opened. ");
    callback.onStreamConnected();
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
    try {
      clientApi
          .getFeatureConfigByIdentifierAsync(
              identifier,
              environmentID,
              cluster,
              new ApiCallback<FeatureConfig>() {
                @Override
                public void onFailure(
                    ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                  log.error("Failed to sync the feature {} due to {}", identifier, e.getMessage());
                }

                @Override
                public void onSuccess(
                    FeatureConfig result,
                    int statusCode,
                    Map<String, List<String>> responseHeaders) {
                  repository.setFlag(result.getFeature(), result);
                }

                @Override
                public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

                @Override
                public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
              })
          .execute();
    } catch (ApiException | IOException e) {

      log.error("Failed to sync the feature {} due to {}", identifier, e.getMessage());
    }
  }

  private void processSegment(JsonObject jsonObject) {

    log.info("Syncing the latest segments..");
    String identifier = jsonObject.get("identifier").getAsString();
    try {
      clientApi
          .getSegmentByIdentifierAsync(
              identifier,
              environmentID,
              cluster,
              new ApiCallback<Segment>() {
                @Override
                public void onFailure(
                    ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                  log.error("Failed to sync the segment {} due to {}", identifier, e.getMessage());
                }

                @Override
                public void onSuccess(
                    Segment result, int statusCode, Map<String, List<String>> responseHeaders) {
                  repository.setSegment(result.getIdentifier(), result);
                }

                @Override
                public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

                @Override
                public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
              })
          .execute();

    } catch (ApiException | IOException e) {
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
    callback.onStreamDisconnected();
  }

  @Override
  public Request onPreRetry(ServerSentEvent serverSentEvent, Request request) {

    return null;
  }

  void stop() {}
}