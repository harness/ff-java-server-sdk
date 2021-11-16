package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import java.util.Optional;
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
  }

  @Override
  protected void runOneIteration() {
    final Optional<String> token = connector.authenticate(callback::onAuthError);
    if (token.isPresent()) {
      callback.onAuthSuccess();
      log.info("Stopping Auth service");
      this.stopAsync();
    }
  }

  @Override
  @NonNull
  protected Scheduler scheduler() {

    return Scheduler.newFixedDelaySchedule(0L, pollIntervalInSec, TimeUnit.SECONDS);
  }

  public void close() {
    stopAsync();
  }
}
