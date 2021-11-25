package io.harness.cf.client.connector;

import io.harness.cf.client.dto.Message;

public interface Updater {
  void onConnected();

  void onDisconnected();

  void onReady();

  void onFailure(final String message);

  void onError();

  void update(final Message message);
}
