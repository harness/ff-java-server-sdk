package io.harness.cf.client.api;

interface AuthCallback {
  void onAuthSuccess();

  void onFailure(final String message);
}
