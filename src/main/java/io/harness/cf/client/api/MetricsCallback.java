package io.harness.cf.client.api;

import lombok.NonNull;

interface MetricsCallback {
  void onMetricsReady();

  void onMetricsError(@NonNull String error);

  void onMetricsFailure();
}
