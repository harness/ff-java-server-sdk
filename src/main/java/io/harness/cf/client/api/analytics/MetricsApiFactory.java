package io.harness.cf.client.api.analytics;

import com.google.common.base.Strings;
import io.harness.cf.ApiClient;
import io.harness.cf.api.MetricsApi;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * This is a factory class to provide the API for metrics related operations.
 *
 * @author Subir.Adhikari
 * @version 1.0
 * @since 08/01/2021
 */
@Slf4j
@UtilityClass
public class MetricsApiFactory {
  private static final long AUTH_RETRY_INTERNAL_MILLIS = 1000;
  private static final int AUTH_RETRY_MAX_RETRY_COUNT = 3;

  public static MetricsApi create(
      String basePath, int connectionTimeout, int readTimeout, int writeTimeout, boolean debug) {

    MetricsApi metricsAPI = new MetricsApi();
    if (!Strings.isNullOrEmpty(basePath)) {
      ApiClient apiClient = metricsAPI.getApiClient();
      apiClient.setConnectTimeout(connectionTimeout);
      apiClient.setReadTimeout(readTimeout);
      apiClient.setWriteTimeout(writeTimeout);
      apiClient.setBasePath(basePath);
      apiClient.setDebugging(debug);
      apiClient.setUserAgent("java " + io.harness.cf.Version.VERSION);
      String hostname = "UnknownHost";
      try {
        hostname = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        log.warn("Unable to get hostname");
      }
      apiClient.addDefaultHeader("Hostname", hostname);
      metricsAPI.setApiClient(apiClient);
    }

    return metricsAPI;
  }

  public static void addAuthHeader(MetricsApi defaultApi, String jwtToken) {
    ApiClient apiClient = defaultApi.getApiClient();
    apiClient.addDefaultHeader("Authorization", "Bearer " + jwtToken);
    defaultApi.setApiClient(apiClient);
  }
}
