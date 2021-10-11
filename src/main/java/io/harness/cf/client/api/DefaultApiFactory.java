package io.harness.cf.client.api;

import com.google.common.base.Strings;
import io.harness.cf.ApiClient;
import io.harness.cf.api.ClientApi;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class DefaultApiFactory {

  public static ClientApi create(
      String basePath, int connectionTimeout, int readTimeout, int writeTimeout, boolean debug) {

    ClientApi defaultApi = new ClientApi();
    if (!Strings.isNullOrEmpty(basePath)) {
      ApiClient apiClient = defaultApi.getApiClient();
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
      defaultApi.setApiClient(apiClient);
    }

    return defaultApi;
  }

  public static void addAuthHeader(ClientApi defaultApi, String jwtToken) {
    ApiClient apiClient = defaultApi.getApiClient();
    apiClient.addDefaultHeader("Authorization", "Bearer " + jwtToken);
    defaultApi.setApiClient(apiClient);
  }
}
