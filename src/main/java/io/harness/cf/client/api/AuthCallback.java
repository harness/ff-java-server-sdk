package io.harness.cf.client.api;

import lombok.NonNull;

interface AuthCallback {
  void onAuthSuccess(@NonNull final String environment, @NonNull String cluster);

  void onAuthError(String error);
}
