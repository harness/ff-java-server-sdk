package io.harness.cf.client.connector;

import com.google.gson.Gson;
import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.client.dto.Message;
import java.util.Map;
import lombok.NonNull;
import okhttp3.Request;
import okhttp3.Response;

public class EventSource implements ServerSentEvent.Listener {

  private final OkSse okSse;
  private final Updater updater;
  private ServerSentEvent sse;
  private final Gson gson = new Gson();

  public EventSource(@NonNull String url, Map<String, String> headers, @NonNull Updater updater) {
    this.updater = updater;
    this.okSse = new OkSse();
    Request.Builder builder = new Request.Builder().url(url);
    headers.forEach(builder::header);
    sse = okSse.newServerSentEvent(builder.build(), this);
    updater.onReady();
  }

  @Override
  public void onOpen(ServerSentEvent serverSentEvent, Response response) {
    if (updater != null) {
      updater.onConnected();
    }
  }

  @Override
  public void onMessage(ServerSentEvent sse, String id, String event, String message) {
    Message msg = gson.fromJson(message, Message.class);
    updater.update(msg);
  }

  @Override
  public void onComment(ServerSentEvent serverSentEvent, String s) {}

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
    updater.onDisconnected();
  }

  @Override
  public Request onPreRetry(ServerSentEvent serverSentEvent, Request request) {
    return null;
  }

  public void close() {
    sse.close();
  }
}
