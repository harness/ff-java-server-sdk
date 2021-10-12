package io.harness.cf.client.api.mock;

import static com.google.common.base.Strings.isNullOrEmpty;

import io.harness.cf.api.ClientApi;
import io.harness.cf.client.api.AuthService;
import io.harness.cf.client.api.CfClientException;

public class MockedAuthService extends AuthService {

  public MockedAuthService(
      ClientApi defaultApi, String apiKey, MockedCfClient cfClient, int pollIntervalInSec) {

    super(defaultApi, apiKey, cfClient, pollIntervalInSec);
  }

  @Override
  protected void runOneIteration() throws Exception {

    if (isNullOrEmpty(apiKey)) {

      throw new CfClientException("SDK key cannot be empty");
    }

    cfClient.setJwtToken(String.valueOf(System.currentTimeMillis()));
    ((MockedCfClient) cfClient).initialize();
    this.stopAsync();
  }
}
