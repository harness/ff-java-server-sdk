package io.harness.cf.client.api;

import io.harness.cf.client.api.mock.MockedCfClient;
import io.harness.cf.client.api.mock.MockedCfConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class CallbacksTest {

    @Test
    public void testCallbacks() {

        final CountDownLatch latch = new CountDownLatch(1);
        final MockedCfClient cfClient = new MockedCfClient();
        final String apiKey = String.valueOf(System.currentTimeMillis());
        final MockedCfConfiguration cfConfiguration = new MockedCfConfiguration();

        cfClient.initialize(

                apiKey,
                cfConfiguration,
                (success, error) -> {

                    Assert.assertTrue(success);
                    Assert.assertNull(error);
                    latch.countDown();
                }
        );

        try {

            final boolean result = latch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue(result);

        } catch (InterruptedException e) {

            Assert.fail(e.getMessage());
        }
    }
}
