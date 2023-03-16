package io.harness.cf.client.api;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MissingSdkKeyException extends RuntimeException {

  private static final String MISSING_SDK_KEY = "SDK key cannot be empty!";

  public MissingSdkKeyException() {
    super(MISSING_SDK_KEY);
    log.error(MISSING_SDK_KEY);
  }

  public MissingSdkKeyException(Exception ex) {
    super(MISSING_SDK_KEY, ex);
    log.error(MISSING_SDK_KEY, ex);
  }
}
