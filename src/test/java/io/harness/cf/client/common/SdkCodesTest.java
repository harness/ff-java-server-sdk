package io.harness.cf.client.common;

import static io.harness.cf.client.common.SdkCodes.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.harness.cf.client.dto.Target;
import org.junit.jupiter.api.Test;

class SdkCodesTest {

  @Test
  void testAllLogs() {
    assertDoesNotThrow(
        () -> {
          errorMissingSdkKey();
          infoPollStarted(123);
          infoSdkInitOk();
          infoSdkAuthOk();
          infoPollingStopped();
          infoStreamConnected();
          infoStreamEventReceived(null);
          infoStreamEventReceived("dummy data");
          infoMetricsThreadStarted(321);
          infoMetricsThreadExited();
          warnAuthFailedSrvDefaults(null);
          warnAuthFailedSrvDefaults("error 1");
          warnAuthRetying(1);
          warnAuthRetying(-1);
          warnStreamDisconnected("error 2");
          warnStreamDisconnected(null);
          warnPostMetricsFailed(null);
          warnPostMetricsFailed("error 3");
          warnDefaultVariationServed("id1", null, null);
          warnDefaultVariationServed("id1", null, "defaultVal");

          Target target = Target.builder().identifier("test").isPrivate(false).build();
          warnDefaultVariationServed("id2", target, "defaultVal2");
        });
  }
}
