package io.harness.cf.client.api;

import io.harness.cf.ApiClient;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class ApiFactory {

  public static ApiClient create(
      String basePath, int connectionTimeout, int readTimeout, int writeTimeout, boolean debug) {

    ApiClient apiClient = new ApiClient();
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

    return apiClient;
  }

  public static void addAuthHeader(ApiClient apiClient, String jwtToken) {
    apiClient.addDefaultHeader("Authorization", "Bearer " + jwtToken);
  }
}
