package io.harness.cf.client.api;

import lombok.NonNull;

interface PollerCallback {
  void onPollerReady();

  void onPollerFailed(@NonNull String error);

  void onPollerError(@NonNull String error);
}
