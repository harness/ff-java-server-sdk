package io.harness.cf.client.api.analytics;

import com.google.common.base.Strings;
import io.harness.cf.ApiException;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.DefaultApiFactory;
import io.harness.cf.client.dto.AuthenticationRequestBuilder;
import io.harness.cf.metrics.ApiClient;
import io.harness.cf.metrics.api.DefaultApi;
import io.harness.cf.model.AuthenticationResponse;
import lombok.SneakyThrows;
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

  @SneakyThrows
  public static DefaultApi create(String apiKey, String basePath) {
    if (Strings.isNullOrEmpty(apiKey)) {
      throw new CfClientException("SDK key cannot be empty");
    }
    DefaultApi metricsAPI = new DefaultApi();
    io.harness.cf.api.DefaultApi clientAPI = DefaultApiFactory.create(apiKey, basePath);
    if (!Strings.isNullOrEmpty(basePath)) {
      ApiClient apiClient = metricsAPI.getApiClient();
      apiClient.setBasePath(basePath);
      metricsAPI.setApiClient(apiClient);
    }

    int count = 0;
    while (count < AUTH_RETRY_MAX_RETRY_COUNT) {
      try {
        auth(metricsAPI, apiKey, clientAPI);
        break;
      } catch (Exception apiException) {
        count++;
        log.error("Failed to get auth token {}", apiException.getMessage());
        Thread.sleep(AUTH_RETRY_INTERNAL_MILLIS);
      }
    }

    return metricsAPI;
  }

  public static void auth(
      DefaultApi metricsAPI, String apiKey, io.harness.cf.api.DefaultApi clientAPI)
      throws CfClientException {
    String authToken = getAuthToken(clientAPI, apiKey);
    ApiClient apiClient = metricsAPI.getApiClient();
    apiClient.addDefaultHeader("Authorization", "Bearer " + authToken);
    metricsAPI.setApiClient(apiClient);
  }

  @SneakyThrows
  public static String getAuthToken(io.harness.cf.api.DefaultApi defaultApi, String apiKey)
      throws CfClientException {
    AuthenticationResponse authResponse = null;

    try {
      authResponse =
          defaultApi.authenticate(
              AuthenticationRequestBuilder.anAuthenticationRequest().apiKey(apiKey).build());
      return authResponse.getAuthToken();
    } catch (ApiException apiException) {
      if (apiException.getCode() == 401) {
        throw new CfClientException(String.format("Invalid apiKey %s. Exiting. ", apiKey));
      }
      log.error("Failed to get auth token {}", apiException.getMessage());
    }

    return null;
  }
}
