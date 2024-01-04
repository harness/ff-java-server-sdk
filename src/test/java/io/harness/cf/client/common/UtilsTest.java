package io.harness.cf.client.common;

import static io.harness.cf.client.common.Utils.isEmpty;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class UtilsTest {
  @Test
  public void testIsEmpty() {
    assertTrue(isEmpty(null));
    assertTrue(isEmpty(Collections.emptyList()));
    assertFalse(isEmpty(Arrays.asList(1, 2, 3)));
  }
}
