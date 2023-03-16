package io.harness.cf.client.connector;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import io.harness.cf.client.api.MissingSdkKeyException;
import org.junit.jupiter.api.Test;

class HarnessConnectorTest {

  @Test
  void shouldThrowExceptionWhenEmptyApiKeyIsGiven() {

    Exception thrown =
        assertThrows(
            MissingSdkKeyException.class,
            () -> new HarnessConnector("", mock(HarnessConfig.class)),
            "Exception was not thrown");
    assertInstanceOf(MissingSdkKeyException.class, thrown);
  }

  @Test
  void shouldThrowExceptionWhenNullApiKeyIsGiven() {

    Exception thrown =
        assertThrows(
            NullPointerException.class,
            () -> new HarnessConnector(null, mock(HarnessConfig.class)),
            "Exception was not thrown");
    assertInstanceOf(NullPointerException.class, thrown);
  }
}
