package io.harness.cf.client.connector;

import io.harness.cf.client.dto.Message;

public interface Updater {
  void onConnected();

  void onDisconnected();

  void onReady();

  void onFailure();

  void onError();

  void update(Message message);
}
