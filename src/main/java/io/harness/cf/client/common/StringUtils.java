package io.harness.cf.client.common;

import javax.annotation.CheckForNull;

public class StringUtils {

  /** Private constructor to disallow instantiating util class */
  private StringUtils() {}

  public static boolean isNullOrEmpty(@CheckForNull String string) {
    return string == null || string.isEmpty();
  }
}
