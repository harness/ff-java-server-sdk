package io.harness.cf.client.api;

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.model.AuthenticationRequest;
import io.harness.cf.model.AuthenticationResponse;

import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthService extends AbstractScheduledService {

    protected final String apiKey;
    protected final CfClient cfClient;
    protected final ClientApi defaultApi;
    protected final int pollIntervalInSec;
    protected final AuthCallback callback;

    public AuthService(

            final ClientApi defaultApi,
            final String apiKey,
            final CfClient cfClient,
            final int pollIntervalInSec,
            final AuthCallback callback
    ) {

        this.apiKey = apiKey;
        this.cfClient = cfClient;
        this.callback = callback;
        this.defaultApi = defaultApi;
        this.pollIntervalInSec = pollIntervalInSec;
    }

    @Override
    protected void runOneIteration() throws Exception {

        if (isNullOrEmpty(apiKey)) {

            final Exception error = new CfClientException("SDK key cannot be empty");
            failure(error);
            throw error;
        }

        try {

            final AuthenticationRequest request = AuthenticationRequest.builder()
                    .apiKey(apiKey)
                    .build();

            final AuthenticationResponse response = defaultApi.authenticate(request);

            final String token = response.getAuthToken();
            cfClient.setJwtToken(token);
            cfClient.init();

            log.info("Stopping Auth service");
            success();
            this.stopAsync();

        } catch (ApiException apiException) {

            log.error(

                    "Failed to get auth token {}",
                    apiException.getMessage()
            );

            if (apiException.getCode() == 401 || apiException.getCode() == 403) {

                String errorMsg = String.format(

                        "Invalid apiKey %s. Serving default value. ",
                        apiKey
                );

                log.error(errorMsg);

                final Exception error = new CfClientException(errorMsg);
                failure(error);
                throw error;
            }

            failure(apiException);
        }
    }

    @Override
    @NonNull
    protected Scheduler scheduler() {

        return Scheduler.newFixedDelaySchedule(0L, pollIntervalInSec, TimeUnit.SECONDS);
    }

    protected void failure(final Exception error) {

        if (callback != null) {

            callback.onSuccess(false, error);
        }
    }

    protected void success() {

        if (callback != null) {

            callback.onSuccess(true, null);
        }
    }
}
