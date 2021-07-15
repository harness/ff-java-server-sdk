package io.harness.cf.client.api.mock;

import io.harness.cf.client.api.analytics.AnalyticsEventHandler;
import io.harness.cf.client.api.analytics.AnalyticsPublisherService;
import io.harness.cf.client.api.analytics.Cache;
import io.harness.cf.client.dto.Analytics;
import java.util.HashSet;
import java.util.Set;

public class MockedAnalyticsHandler extends AnalyticsEventHandler {

  private final Set<MockedAnalyticsHandlerCallback> callbacks;

  {
    callbacks = new HashSet<>();
  }

  public MockedAnalyticsHandler(
      Cache analyticsCache, AnalyticsPublisherService analyticsPublisherService) {

    super(analyticsCache, analyticsPublisherService);
  }

  public void addCallback(MockedAnalyticsHandlerCallback callback) {

    callbacks.add(callback);
  }

  public void removeCallback(MockedAnalyticsHandlerCallback callback) {

    callbacks.remove(callback);
  }

  @Override
  protected void onMetricsEvent(Analytics analytics) {

    super.onMetricsEvent(analytics);
    notifyMetrics();
  }

  @Override
  protected void onTimerEvent() {

    super.onTimerEvent();
    notifyTimer();
  }

  private void notifyMetrics() {

    for (MockedAnalyticsHandlerCallback callback : callbacks) {

      callback.onMetrics();
    }
  }

  private void notifyTimer() {

    for (MockedAnalyticsHandlerCallback callback : callbacks) {

      callback.onTimer();
    }
  }
}
