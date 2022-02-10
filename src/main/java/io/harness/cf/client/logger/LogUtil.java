package io.harness.cf.client.logger;

import io.harness.cf.Version;

public class LogUtil {
  public static void setSystemProps() {
    System.setProperty("SDK", "JAVA");
    System.setProperty("version", Version.VERSION);
  }
}
