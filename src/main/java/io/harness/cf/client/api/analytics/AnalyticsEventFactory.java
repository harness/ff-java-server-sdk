package io.harness.cf.client.api.analytics;

import com.lmax.disruptor.EventFactory;
import io.harness.cf.client.dto.Analytics;

/**
 * This class implements the EventFactory interface required by the LMAX library.
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
public class AnalyticsEventFactory implements EventFactory<Analytics> {
  @Override
  public Analytics newInstance() {
    return new Analytics();
  }
}
