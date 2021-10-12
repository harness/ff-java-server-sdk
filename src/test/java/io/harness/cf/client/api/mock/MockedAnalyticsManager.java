package io.harness.cf.client.api.mock;

import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.analytics.AnalyticsEventHandler;
import io.harness.cf.client.api.analytics.AnalyticsManager;
import io.harness.cf.client.api.analytics.AnalyticsPublisherService;
import javax.annotation.Nonnull;

public class MockedAnalyticsManager extends AnalyticsManager {

  private MockedAnalyticsHandler analyticsEventHandler;

  public MockedAnalyticsManager(String environmentID, Config config) throws CfClientException {
    super(new io.harness.cf.api.MetricsApi(), environmentID, "", config);
  }

  @Nonnull
  @Override
  protected AnalyticsEventHandler getAnalyticsEventHandler(
      AnalyticsPublisherService analyticsPublisherService) {

    if (analyticsEventHandler == null) {

      analyticsEventHandler = new MockedAnalyticsHandler(analyticsCache, analyticsPublisherService);
    }
    return analyticsEventHandler;
  }

  public void addCallback(MockedAnalyticsHandlerCallback callback) throws IllegalStateException {

    if (analyticsEventHandler == null) {

      throw new IllegalStateException("Analytics event handler not yet instantiated");
    }
    analyticsEventHandler.addCallback(callback);
  }

  public void removeCallback(MockedAnalyticsHandlerCallback callback) throws IllegalStateException {

    if (analyticsEventHandler == null) {

      throw new IllegalStateException("Analytics event handler not yet instantiated");
    }
    analyticsEventHandler.removeCallback(callback);
  }
}
