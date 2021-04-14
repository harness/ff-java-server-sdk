package io.harness.cf.client.api;

import com.google.common.base.Strings;
import io.harness.cf.ApiClient;
import io.harness.cf.api.DefaultApi;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class DefaultApiFactory {

  @SneakyThrows
  public static DefaultApi create(
      String basePath, int connectionTimeout, int readTimeout, int writeTimeout) {
    DefaultApi defaultApi = new DefaultApi();
    if (!Strings.isNullOrEmpty(basePath)) {
      ApiClient apiClient = defaultApi.getApiClient();
      apiClient.setConnectTimeout(connectionTimeout);
      apiClient.setReadTimeout(readTimeout);
      apiClient.setWriteTimeout(writeTimeout);
      apiClient.setBasePath(basePath);
      defaultApi.setApiClient(apiClient);
    }

    return defaultApi;
  }

  public static void addAuthHeader(DefaultApi defaultApi, String jwtToken) {
    ApiClient apiClient = defaultApi.getApiClient();
    apiClient.addDefaultHeader("Authorization", "Bearer " + jwtToken);
    defaultApi.setApiClient(apiClient);
  }
}
