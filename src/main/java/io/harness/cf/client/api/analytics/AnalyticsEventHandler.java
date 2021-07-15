package io.harness.cf.client.api.analytics;

import com.lmax.disruptor.EventHandler;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.dto.Analytics;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumer class for consuming the incoming objects in the LMAX ring buffer. It has the following
 * functionalities 1) Listens to the queue and take out the incoming object 2) Place them
 * appropriately in the cache for further processing
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
@Slf4j
public class AnalyticsEventHandler implements EventHandler<Analytics> {
  private final Cache analyticsCache;
  private final AnalyticsPublisherService analyticsPublisherService;

  public AnalyticsEventHandler(
      Cache analyticsCache, AnalyticsPublisherService analyticsPublisherService) {
    this.analyticsCache = analyticsCache;
    this.analyticsPublisherService = analyticsPublisherService;
  }

  @Override
  public void onEvent(Analytics analytics, long l, boolean b) {

    switch (analytics.getEventType()) {
      case TIMER:
        onTimerEvent();
        break;

      case METRICS:
        onMetricsEvent(analytics);
        break;
    }
  }

  protected void onMetricsEvent(Analytics analytics) {

    log.debug(
        "Analytics object received in queue: Target:{},FeatureFlag:{}",
        analytics.getTarget().getIdentifier(),
        analytics.getFeatureConfig().getFeature());

    Integer count = analyticsCache.get(analytics);
    if (count == null) {

      analyticsCache.put(analytics, 1);
    } else {

      analyticsCache.put(analytics, count + 1);
    }
  }

  protected void onTimerEvent() {

    try {

      analyticsPublisherService.sendDataAndResetCache();
    } catch (CfClientException e) {

      log.warn("Failed to send analytics data to server", e);
    }
  }

  // Uncomment the below line to print the cache for debugging purpose
  // AnalyticsManager.analyticsCache.printCache();

}
