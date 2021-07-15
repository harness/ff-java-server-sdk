package io.harness.cf.client.api;

import io.harness.cf.client.api.mock.MockedCfClient;
import io.harness.cf.client.api.mock.MockedCfConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class CfClientMetricsTest {

  @Test
  public void testMetrics() {

    final String apiKey = String.valueOf(System.currentTimeMillis());
    final MockedCfClient cfClient = new MockedCfClient(apiKey);

    final AtomicBoolean initOk = new AtomicBoolean();
    final CountDownLatch latch = new CountDownLatch(1);

    final MockedCfConfiguration cfConfiguration = new MockedCfConfiguration();

    while (!cfClient.isInitialized()) {

      Thread.yield();
    }
  }
}
