package io.harness.cf.client.connector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.cf.ApiClient;
import io.harness.cf.api.ClientApi;
import io.harness.cf.api.MetricsApi;
import io.harness.cf.client.api.MissingSdkKeyException;
import io.harness.cf.client.api.dispatchers.CannedResponses;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;

class HarnessConnectorTest {

  @Test
  void shouldThrowExceptionWhenEmptyApiKeyIsGiven() {

    Exception thrown =
        assertThrows(
            MissingSdkKeyException.class,
            () -> new HarnessConnector("", mock(HarnessConfig.class)),
            "Exception was not thrown");
    assertInstanceOf(MissingSdkKeyException.class, thrown);
  }

  @Test
  void shouldThrowExceptionWhenNullApiKeyIsGiven() {

    Exception thrown =
        assertThrows(
            NullPointerException.class,
            () -> new HarnessConnector(null, mock(HarnessConfig.class)),
            "Exception was not thrown");
    assertInstanceOf(NullPointerException.class, thrown);
  }

  <T> Answer<T> makeCaptureHeadersAnswer(Map<String, String> capturedHeaders) {
    return params -> {
      String h = String.valueOf((String) params.getArgument(0));
      String v = String.valueOf((String) params.getArgument(1));
      System.out.printf("adding %s=%s\n", h, v);
      capturedHeaders.put(h, v);
      return null;
    };
  }

  void setupHeaderCaptures(
      ClientApi mockClientApi,
      Map<String, String> capturedApiHeaders,
      MetricsApi mockMetricsApi,
      Map<String, String> capturedMetricApiHeaders) {
    final ApiClient mockInternalApiClient = mock(ApiClient.class);
    when(mockClientApi.getApiClient()).thenReturn(mockInternalApiClient);
    when(mockInternalApiClient.addDefaultHeader(anyString(), anyString()))
        .thenAnswer(makeCaptureHeadersAnswer(capturedApiHeaders));

    final ApiClient mockInternalMetricsApiClient = mock(ApiClient.class);
    when(mockMetricsApi.getApiClient()).thenReturn(mockInternalMetricsApiClient);
    when(mockInternalMetricsApiClient.addDefaultHeader(anyString(), anyString()))
        .thenAnswer(makeCaptureHeadersAnswer(capturedMetricApiHeaders));
  }

  @ParameterizedTest
  @NullSource()
  @ValueSource(strings = {"", " ", "\t", "\n", "\r"})
  void shouldParseJwtTokenWithMissingAccountId(String accountId) {
    final Map<String, String> apiHeaders = new HashMap<>();
    final Map<String, String> metricApiHeaders = new HashMap<>();

    final ClientApi mockClientApi = mock(ClientApi.class);
    final MetricsApi mockMetricsApi = mock(MetricsApi.class);
    setupHeaderCaptures(mockClientApi, apiHeaders, mockMetricsApi, metricApiHeaders);

    final HarnessConnector connector =
        new HarnessConnector(
            "dummy_sdk_key", mock(HarnessConfig.class), mockClientApi, mockMetricsApi);

    final String token = CannedResponses.makeDummyJwtToken("dummyUUID", "dev", accountId);
    connector.processToken(token);

    for (Map<String, String> nextMap : Arrays.asList(apiHeaders, metricApiHeaders)) {
      System.out.print(nextMap);
      assertEquals(2, nextMap.size());
      assertEquals("Bearer " + token, nextMap.get("Authorization"));
      assertEquals("dev", nextMap.get("Harness-EnvironmentID"));
      assertFalse(nextMap.containsKey("Harness-AccountID"));
    }
  }

  @Test
  void shouldAddHarnessEnvironmentIdHeader() {
    final Map<String, String> apiHeaders = new HashMap<>();
    final Map<String, String> metricApiHeaders = new HashMap<>();

    final ClientApi mockClientApi = mock(ClientApi.class);
    final MetricsApi mockMetricsApi = mock(MetricsApi.class);
    setupHeaderCaptures(mockClientApi, apiHeaders, mockMetricsApi, metricApiHeaders);

    final HarnessConnector connector =
        new HarnessConnector(
            "dummy_sdk_key", mock(HarnessConfig.class), mockClientApi, mockMetricsApi);

    final String token = CannedResponses.makeDummyJwtToken("dummyUUID", "non_uuid_env_name", "acc");
    connector.processToken(token);

    for (Map<String, String> nextMap : Arrays.asList(apiHeaders, metricApiHeaders)) {
      System.out.print(nextMap);
      assertEquals(3, nextMap.size());
      assertEquals("Bearer " + token, nextMap.get("Authorization"));
      assertEquals("non_uuid_env_name", nextMap.get("Harness-EnvironmentID"));
      assertEquals("acc", nextMap.get("Harness-AccountID"));
    }
  }

  @ParameterizedTest
  @NullSource()
  @ValueSource(strings = {"", " ", "\t", "\n", "\r"})
  void shouldAddHarnessEnvironmentIdHeaderButFallbackToUuidEnvIfEnvNotPresent(String env) {
    final Map<String, String> apiHeaders = new HashMap<>();
    final Map<String, String> metricApiHeaders = new HashMap<>();

    final ClientApi mockClientApi = mock(ClientApi.class);
    final MetricsApi mockMetricsApi = mock(MetricsApi.class);
    setupHeaderCaptures(mockClientApi, apiHeaders, mockMetricsApi, metricApiHeaders);

    final HarnessConnector connector =
        new HarnessConnector(
            "dummy_sdk_key", mock(HarnessConfig.class), mockClientApi, mockMetricsApi);

    final String token = CannedResponses.makeDummyJwtToken("dummyUUID", env, "acc");
    connector.processToken(token);

    for (Map<String, String> nextMap : Arrays.asList(apiHeaders, metricApiHeaders)) {
      System.out.print(nextMap);
      assertEquals(3, nextMap.size());
      assertEquals("Bearer " + token, nextMap.get("Authorization"));
      assertEquals("dummyUUID", nextMap.get("Harness-EnvironmentID"));
      assertEquals("acc", nextMap.get("Harness-AccountID"));
    }
  }

  @ParameterizedTest
  @NullSource()
  @ValueSource(strings = {"", " ", "\t", "\n", "\r"})
  void shouldNotAddHarnessEnvironmentIdHeaderIfNeitherEnvOrEnvUuidPresent(String env) {
    final Map<String, String> apiHeaders = new HashMap<>();
    final Map<String, String> metricApiHeaders = new HashMap<>();

    final ClientApi mockClientApi = mock(ClientApi.class);
    final MetricsApi mockMetricsApi = mock(MetricsApi.class);
    setupHeaderCaptures(mockClientApi, apiHeaders, mockMetricsApi, metricApiHeaders);

    final HarnessConnector connector =
        new HarnessConnector(
            "dummy_sdk_key", mock(HarnessConfig.class), mockClientApi, mockMetricsApi);

    final String token = CannedResponses.makeDummyJwtToken(null, env, "acc");
    connector.processToken(token);

    for (Map<String, String> nextMap : Arrays.asList(apiHeaders, metricApiHeaders)) {
      System.out.print(nextMap);
      assertEquals(2, nextMap.size());
      assertEquals("Bearer " + token, nextMap.get("Authorization"));
      assertEquals("acc", nextMap.get("Harness-AccountID"));
      assertFalse(nextMap.containsKey("Harness-EnvironmentID"));
    }
  }
}
