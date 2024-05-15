package io.harness.cf.client.common;

import static io.harness.cf.client.common.SdkCodes.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import io.harness.cf.client.api.BaseConfig;
import io.harness.cf.client.dto.Target;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SdkCodesTest {

  @Test
  void testAllLogs() {
    assertDoesNotThrow(
        () -> {
          BaseConfig no6001logs = Mockito.mock(BaseConfig.class);
          when(no6001logs.isSdkCode6001Suppressed()).thenReturn(true);

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
          warnDefaultVariationServed("id1_6001visible", null, null, Mockito.mock(BaseConfig.class));
          warnDefaultVariationServed(
              "id1_6001visible", null, "defaultVal", Mockito.mock(BaseConfig.class));

          Target target = Target.builder().identifier("test").isPrivate(false).build();
          warnDefaultVariationServed(
              "id2_6001visible", target, "defaultVal2", Mockito.mock(BaseConfig.class));

          warnDefaultVariationServed("id1_6001notvisible", null, null, no6001logs);
          warnDefaultVariationServed("id1_6001notvisible", null, "defaultVal", no6001logs);
          warnDefaultVariationServed("id2_6001notvisible", target, "defaultVal2", no6001logs);
        });
  }
}
