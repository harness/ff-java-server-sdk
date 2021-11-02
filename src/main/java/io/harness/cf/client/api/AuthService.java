package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.model.AuthenticationRequest;
import io.harness.cf.model.AuthenticationResponse;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class AuthService extends AbstractScheduledService {

  private final ClientApi clientApi;
  private final String apiKey;
  private final int pollIntervalInSec;
  private final AuthCallback callback;

  public AuthService(
      @NonNull final ClientApi clientApi,
      @NonNull final String apiKey,
      final int pollIntervalInSec,
      final AuthCallback callback) {

    this.apiKey = apiKey;
    this.clientApi = clientApi;
    this.pollIntervalInSec = pollIntervalInSec;
    this.callback = callback;
  }

  @Override
  protected void runOneIteration() {
    try {

      final AuthenticationRequest request = AuthenticationRequest.builder().apiKey(apiKey).build();

      final AuthenticationResponse response = clientApi.authenticate(request);

      final String token = response.getAuthToken();
      callback.onAuthSuccess(token);
      log.info("Stopping Auth service");
      this.stopAsync();

    } catch (ApiException apiException) {

      log.error("Failed to get auth token {}", apiException.getMessage());

      if (apiException.getCode() == 401 || apiException.getCode() == 403) {

        String errorMsg = String.format("Invalid apiKey %s. Serving default value. ", apiKey);

        log.error(errorMsg);
        callback.onAuthError(errorMsg);
      }

      callback.onAuthError(apiException.getMessage());
    }
  }

  @Override
  @NonNull
  protected Scheduler scheduler() {

    return Scheduler.newFixedDelaySchedule(0L, pollIntervalInSec, TimeUnit.SECONDS);
  }
}
