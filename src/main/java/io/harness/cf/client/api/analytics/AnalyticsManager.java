package io.harness.cf.client.api.analytics;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Analytics;
import io.harness.cf.client.dto.EventType;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * This class handles various analytics service related components and prepares them 1) It creates
 * the LMAX ring buffer 2) It pushes data to the buffer and publishes it for consumption 3)
 * Initilazes the cache for analytics
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
@Slf4j
public class AnalyticsManager {
  private final Cache analyticsCache;
  private final RingBuffer<Analytics> ringBuffer;

  public AnalyticsManager(String environmentID, String apiKey, Config config)
      throws CfClientException {
    this.analyticsCache = AnalyticsCacheFactory.create(config.getAnalyticsCacheType());
    AnalyticsPublisherService analyticsPublisherService =
        new AnalyticsPublisherService(apiKey, config, environmentID, analyticsCache);
    ringBuffer = createRingBuffer(config.getBufferSize(), analyticsPublisherService);
    ScheduledExecutorService timerExecutorService = Executors.newSingleThreadScheduledExecutor();
    TimerTask timerTask = new TimerTask(ringBuffer);
    timerExecutorService.scheduleAtFixedRate(timerTask, 0, config.getFrequency(), TimeUnit.SECONDS);
  }

  private RingBuffer<Analytics> createRingBuffer(
      int bufferSize, AnalyticsPublisherService analyticsPublisherService) {
    // The factory for the event
    AnalyticsEventFactory factory = new AnalyticsEventFactory();

    // Construct the Disruptor
    Disruptor<Analytics> disruptor =
        new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE);

    // Connect the handler
    disruptor.handleEventsWith(
        new AnalyticsEventHandler(analyticsCache, analyticsPublisherService));

    // Start the Disruptor, starts all threads running
    disruptor.start();

    // Get the ring buffer from the Disruptor to be used for publishing.
    return disruptor.getRingBuffer();
  }

  // push the incoming data to the ring buffer
  public void pushToQueue(Target target, FeatureConfig featureConfig, Variation variation) {
    Analytics analytics =
        Analytics.builder()
            .featureConfig(featureConfig)
            .target(target)
            .variation(variation)
            .eventType(EventType.METRICS)
            .build();
    long sequence = -1;
    try {
      sequence = ringBuffer.tryNext(); // Grab the next sequence
      Analytics event = ringBuffer.get(sequence); // Get the entry in the Disruptor for the sequence
      event.setFeatureConfig(analytics.getFeatureConfig());
      event.setTarget(analytics.getTarget());
      event.setVariation(analytics.getVariation());
    } catch (InsufficientCapacityException e) {
      log.debug("Insufficient capacity in the analytics ringBuffer");
    } finally {
      if (sequence != -1) {
        ringBuffer.publish(sequence);
      }
    }
  }
}
