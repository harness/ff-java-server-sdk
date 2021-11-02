package io.harness.cf.client.api;

import lombok.NonNull;

interface AuthCallback {
  void onAuthSuccess(@NonNull final String token);

  void onAuthError(String error);
}
