package io.harness.cf.client.api.mock;

import static io.harness.cf.client.api.DefaultApiFactory.addAuthHeader;

import io.harness.cf.ApiException;
import io.harness.cf.client.Evaluation;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.Evaluator;
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

  @NotNull
  @Override
  protected AnalyticsManager getAnalyticsManager() throws CfClientException {

    if (analyticsManager == null) {

      analyticsManager = new MockedAnalyticsManager(environmentID, config);
    }
    return analyticsManager;
  }

  @NotNull
  @Override
  protected MockedAuthService getAuthService(String apiKey, Config config) {

    return new MockedAuthService(defaultApi, apiKey, this, config.getPollIntervalInSeconds());
  }

  public void addCallback(MockedAnalyticsHandlerCallback callback) throws IllegalStateException {

    if (analyticsManager == null) {

      throw new IllegalStateException("Analytics manager not yet instantiated");
    }
    ((MockedAnalyticsManager) analyticsManager).addCallback(callback);
  }

  public void removeCallback(MockedAnalyticsHandlerCallback callback) throws IllegalStateException {

    if (analyticsManager == null) {

      throw new IllegalStateException("Analytics manager not yet instantiated");
    }
    ((MockedAnalyticsManager) analyticsManager).removeCallback(callback);
  }

  @Override
  protected boolean canPushToMetrics(
      Target target, Variation variation, FeatureConfig featureConfig) {

    return target.isValid() && isAnalyticsEnabled && analyticsManager != null;
  }

  public void initialize() throws ApiException, CfClientException {

    doInit();
  }

  @Override
  protected void doInit() throws ApiException, CfClientException {

    addAuthHeader(defaultApi, jwtToken);
    environmentID = getEnvironmentID(jwtToken);
    cluster = getCluster(jwtToken);
    evaluator = new Evaluator(segmentCache);

    initCache(environmentID);

    analyticsManager = getAnalyticsManager();
    isInitialized = true;
  }

  @Override
  protected String getCluster(String jwtToken) {

    return String.valueOf(jwtToken.length());
  }

  @Override
  protected String getEnvironmentID(String jwtToken) {

    return String.valueOf(System.currentTimeMillis());
  }

  @Override
  protected void initCache(String environmentID) {

    final FeatureConfig config = new FeatureConfig();
    config.setKind(FeatureConfig.KindEnum.BOOLEAN);
    featureCache.put(MockedFeatureRepository.MOCK_BOOL, config);
  }

  @NotNull
  @Override
  protected Evaluation getEvaluator() {

    return new MockedEvaluator();
  }
}
