package io.harness.cf.client.api;

import lombok.NonNull;

interface PollerCallback {
  void onPollerReady();

  void onPollerFailed(@NonNull Exception exc);

  void onPollerError(@NonNull Exception exc);
}
