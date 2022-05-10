package io.harness.cf.client.api;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import lombok.NonNull;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class MetricsProcessorTest implements MetricsCallback {
  final int BUFFER_SIZE = 10;
  @Mock private Connector connector;

  private MetricsProcessor metricsProcessor;

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.openMocks(this);
    metricsProcessor =
        Mockito.spy(
            new MetricsProcessor(
                connector, BaseConfig.builder().bufferSize(BUFFER_SIZE).build(), this));
  }

  @Test
  public void testPushToQueue() {
    Target target = Target.builder().identifier("harness").build();
    FeatureConfig feature = FeatureConfig.builder().feature("bool-flag").build();
    Variation variation = Variation.builder().identifier("true").value("true").build();

    for (int i = 0; i < BUFFER_SIZE * 10; i++) {
      metricsProcessor.pushToQueue(target, feature, variation);
    }

    verify(metricsProcessor, times(BUFFER_SIZE - 1)).runOneIteration();
  }

  @Override
  public void onMetricsReady() {
    // not used
  }

  @Override
  public void onMetricsError(@NonNull String error) {
    // not used
  }

  @Override
  public void onMetricsFailure() {
    // not used
  }
}
