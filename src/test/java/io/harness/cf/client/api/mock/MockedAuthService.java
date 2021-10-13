package io.harness.cf.client.api.mock;

import static com.google.common.base.Strings.isNullOrEmpty;

import io.harness.cf.api.ClientApi;
import io.harness.cf.client.api.AuthCallback;
import io.harness.cf.client.api.AuthService;
import io.harness.cf.client.api.CfClientException;

public class MockedAuthService extends AuthService {

    public MockedAuthService(

            final ClientApi defaultApi,
            final String apiKey,
            final MockedCfClient cfClient,
            final int pollIntervalInSec,
            final AuthCallback callback
    ) {

        super(defaultApi, apiKey, cfClient, pollIntervalInSec, callback);
    }

    public MockedAuthService(

            final ClientApi defaultApi,
            final String apiKey,
            final MockedCfClient cfClient,
            final int pollIntervalInSec
    ) {

        super(defaultApi, apiKey, cfClient, pollIntervalInSec, null);
    }

    @Override
    protected void runOneIteration() throws Exception {

        if (isNullOrEmpty(apiKey)) {

            throw new CfClientException("SDK key cannot be empty");
        }

        cfClient.setJwtToken(String.valueOf(System.currentTimeMillis()));
        ((MockedCfClient) cfClient).initialize();
        success();
        this.stopAsync();
    }
}
