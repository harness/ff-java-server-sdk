package io.harness.cf.client.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  public void isNullOrEmpty() {
    assertTrue(StringUtils.isNullOrEmpty(""));
    assertTrue(StringUtils.isNullOrEmpty(null));

    assertFalse(StringUtils.isNullOrEmpty(" "));
    assertFalse(StringUtils.isNullOrEmpty("Some Value"));
    assertFalse(StringUtils.isNullOrEmpty("Some Value With Spaces "));
  }
}
