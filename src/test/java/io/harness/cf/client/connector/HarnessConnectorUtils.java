package io.harness.cf.client.connector;


public class HarnessConnectorUtils {
  public static HarnessConnector makeConnector(String host, int port) {
    final String url = String.format("http://%s:%s/api/1.0", host, port);
    return new HarnessConnector(
        "dummykey", HarnessConfig.builder().readTimeout(1000).configUrl(url).eventUrl(url).build());
  }

  /**
   * This uses an internal constructor to create a connector with a reduced retry timeout (50ms
   * instead of 2000ms). This allows tests with retry logic to run faster during builds.
   */
  public static HarnessConnector makeConnectorWithMinimalRetryBackOff(String host, int port) {
    final String url = String.format("http://%s:%s/api/1.0", host, port);
    return new HarnessConnector(
        "dummykey",
        HarnessConfig.builder().readTimeout(1000).configUrl(url).eventUrl(url).build(),
        50);
  }
}
