package io.harness.cf.client.api;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.ApiCallback;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
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
  private final String sdkKey;
  private final ClientApi clientApi;
  private final Repository repository;
  private final StreamCallback callback;
  private final String url;
  private final OkSse okSse;
  private ServerSentEvent sse;

  @Setter private String environment;
  @Setter private String cluster;
  @Setter private String token;

  private boolean running;

  StreamProcessor(
      @NonNull String sdkKey,
      ClientApi clientApi,
      Repository repository,
      String url,
      @NonNull StreamCallback callback) {

    this.clientApi = clientApi;
    this.repository = repository;
    this.callback = callback;
    this.url = url;
    this.sdkKey = sdkKey;
    this.okSse = new OkSse();
  }

  public void start() {
    if (running
        || Strings.isNullOrEmpty(environment)
        || Strings.isNullOrEmpty(cluster)
        || Strings.isNullOrEmpty(token)) {
      return;
    }

    final String sseUrl = String.join("", url, "/stream?cluster=" + cluster);

    Request request =
        new Request.Builder()
            .url(String.format(sseUrl, environment))
            .header("Authorization", "Bearer " + token)
            .header("API-Key", sdkKey)
            .build();
    sse = okSse.newServerSentEvent(request, this);
    running = true;
  }

  public void stop() {
    running = false;
    sse.close();
    sse = null;
  }

  @Override
  public void onOpen(ServerSentEvent serverSentEvent, Response response) {

    log.info("SSE connection opened. ");
    callback.onStreamConnected();
  }

  @Override
  public void onMessage(ServerSentEvent sse, String id, String event, String message) {

    JsonObject jsonObject;
    try {
      jsonObject = gson.fromJson(message, JsonObject.class);
      String domain = jsonObject.get("domain").getAsString();
      if (domain.equals("flag")) {
        processFeature(jsonObject);
      } else if (domain.equals("target-segment")) {
        processSegment(jsonObject);
      }
    } catch (JsonSyntaxException ex) {
      log.error("Error converting the message from SSE");
    }
  }

  private void processFeature(JsonObject jsonObject) {

    log.info("Syncing the latest features..");
    String identifier = jsonObject.get("identifier").getAsString();
    try {
      clientApi.getFeatureConfigByIdentifierAsync(
          identifier,
          environment,
          cluster,
          new ApiCallback<FeatureConfig>() {
            @Override
            public void onFailure(
                ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
              log.error("Failed to sync the feature {} due to {}", identifier, e.getMessage());
            }

            @Override
            public void onSuccess(
                FeatureConfig result, int statusCode, Map<String, List<String>> responseHeaders) {
              repository.setFlag(result.getFeature(), result);
            }

            @Override
            public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {}

            @Override
            public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {}
          });
    } catch (ApiException e) {

      log.error("Failed to sync the feature {} due to {}", identifier, e.getMessage());
    }
  }

  private void processSegment(JsonObject jsonObject) {

    log.info("Syncing the latest segments..");
    String identifier = jsonObject.get("identifier").getAsString();
    try {
      clientApi.getSegmentByIdentifierAsync(
          identifier,
          environment,
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
          });

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
    callback.onStreamDisconnected();
  }

  @Override
  public Request onPreRetry(ServerSentEvent serverSentEvent, Request request) {

    return null;
  }
}
