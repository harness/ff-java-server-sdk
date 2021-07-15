package io.harness.cf.client.api.mock;

import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.analytics.AnalyticsManager;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import org.jetbrains.annotations.NotNull;

public class MockedCfClient extends CfClient {

  public MockedCfClient(String apiKey) {

    super(apiKey);
  }

  public MockedCfClient(String apiKey, Config config) {

    super(apiKey, config);
  }

  private MockedAnalyticsManager analyticsManager;

  @NotNull
  @Override
  protected AnalyticsManager getAnalyticsManager() throws CfClientException {

    if (analyticsManager == null) {

      analyticsManager = new MockedAnalyticsManager(environmentID, "", config);
    }
    return analyticsManager;
  }

  public void addCallback(MockedAnalyticsHandlerCallback callback) throws IllegalStateException {

    if (analyticsManager == null) {

      throw new IllegalStateException("Analytics manager not yet instantiated");
    }
    analyticsManager.addCallback(callback);
  }

  public void removeCallback(MockedAnalyticsHandlerCallback callback) throws IllegalStateException {

    if (analyticsManager == null) {

      throw new IllegalStateException("Analytics manager not yet instantiated");
    }
    analyticsManager.removeCallback(callback);
  }

  @Override
  protected boolean canPushToMetrics(
      Target target, Variation variation, FeatureConfig featureConfig) {

    return target.isValid() && isAnalyticsEnabled && analyticsManager != null;
  }
}
