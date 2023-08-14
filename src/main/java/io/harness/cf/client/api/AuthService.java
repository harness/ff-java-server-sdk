package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class AuthService extends AbstractScheduledService {

  private final Connector connector;
  private final int pollIntervalInSec;
  private final AuthCallback callback;

  public AuthService(
      @NonNull final Connector connector,
      final int pollIntervalInSec,
      @NonNull final AuthCallback callback) {

    this.connector = connector;
    this.pollIntervalInSec = pollIntervalInSec;
    this.callback = callback;
    log.info("Authentication service initialized");
  }

  @Override
  protected void runOneIteration() {
    try {
      connector.authenticate();
      SdkCodes.infoSdkAuthOk();
      callback.onAuthSuccess();
      stopAsync();
      log.info("Stopping Auth service");
    } catch (ConnectorException e) {
      if (e.shouldRetry()) {
        log.error(
            "Exception while authenticating, retry in {} seconds, error: {}",
            pollIntervalInSec,
            e.getMessage());
      } else {
        stopAsync();
        log.error("Exception while authenticating", e);
        callback.onFailure(e.getMessage());
      }
    }
  }

  @Override
  @NonNull
  protected Scheduler scheduler() {

    return Scheduler.newFixedDelaySchedule(0L, pollIntervalInSec, TimeUnit.SECONDS);
  }

  public void close() {
    stopAsync();
    log.info("Authentication service closed");
  }
}
