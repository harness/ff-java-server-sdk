package io.harness.cf.client.api;

public class FeatureFlagInitializeException extends Exception {
  public FeatureFlagInitializeException() {
    this("Error initializing feature flags SDK");
  }

  public FeatureFlagInitializeException(String message) {
    super(message);
  }

  public FeatureFlagInitializeException(String message, Throwable cause) {
    super(message, cause);
  }

  public FeatureFlagInitializeException(Throwable cause) {
    super(cause);
  }

  public FeatureFlagInitializeException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
