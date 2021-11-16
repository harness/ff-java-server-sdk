package io.harness.cf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
class StreamProcessor implements ServerSentEvent.Listener {
  private final Gson gson = new Gson();
  private final Connector connector;
  private final Repository repository;
  private final StreamCallback callback;
  private final OkSse okSse;
  private ServerSentEvent sse;

  private boolean running;

  StreamProcessor(
      @NonNull Connector connector,
      @NonNull Repository repository,
      @NonNull StreamCallback callback) {

    this.connector = connector;
    this.repository = repository;
    this.callback = callback;
    this.okSse = new OkSse();
    callback.onStreamReady();
  }

  public void start() {
    if (running) {
      log.warn("Environment, cluster is missing!");
      return;
    }

    Request request = connector.stream();
    sse = okSse.newServerSentEvent(request, this);
    running = true;
  }

  public void stop() {
    running = false;
    sse.close();
    sse = null;
  }

  public void close() {
    stop();
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
    Optional<FeatureConfig> flag = connector.getFlag(identifier);
    flag.ifPresent(value -> repository.setFlag(identifier, flag.get()));
  }

  private void processSegment(JsonObject jsonObject) {

    log.info("Syncing the latest segments..");
    String identifier = jsonObject.get("identifier").getAsString();

    Optional<Segment> segment = connector.getSegment(identifier);
    segment.ifPresent(value -> repository.setSegment(identifier, value));
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

    log.info("SSE connection closed.");
    callback.onStreamDisconnected();
  }

  @Override
  public Request onPreRetry(ServerSentEvent serverSentEvent, Request request) {

    return null;
  }
}
