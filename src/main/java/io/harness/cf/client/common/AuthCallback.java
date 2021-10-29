package io.harness.cf.client.common;

import lombok.NonNull;

public interface AuthCallback {
  void onAuthSuccess(@NonNull final String token);

  void onAuthError(String error);
}
