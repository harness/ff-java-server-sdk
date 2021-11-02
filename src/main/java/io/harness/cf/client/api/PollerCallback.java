package io.harness.cf.client.api;

import lombok.NonNull;

interface PollerCallback {
  void onPollerReady();

  void onPollerError(@NonNull String error);
}
