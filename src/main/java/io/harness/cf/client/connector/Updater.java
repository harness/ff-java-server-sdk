package io.harness.cf.client.connector;

import io.harness.cf.client.dto.Message;

public interface Updater {
  void onConnected();

  void onDisconnected(String reason);

  void onReady();

  void onFailure(final String message);

  void update(final Message message);
}
