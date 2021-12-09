package io.harness.cf.client.connector;

import io.harness.cf.ApiClient;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.api.MetricsApi;
import io.harness.cf.model.*;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
public class HarnessConnector implements Connector, AutoCloseable {

  private final ClientApi api;
  private final MetricsApi metricsApi;
  private final String apiKey;
  private final HarnessConfig options;

  private String token;
  private String environment;
  private String cluster;

  private EventSource eventSource;
  private Runnable onUnauthorized;

  public HarnessConnector(@NonNull String apiKey) {
    this(apiKey, HarnessConfig.builder().build());
  }

  public HarnessConnector(@NonNull final String apiKey, @NonNull final HarnessConfig options) {
    this.apiKey = apiKey;
    this.options = options;
    this.api = new ClientApi(makeApiClient());
    this.metricsApi = new MetricsApi(makeMetricsApiClient());
  }

  protected ApiClient makeApiClient() {
    final ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(options.getConfigUrl());
    apiClient.setConnectTimeout(options.getConnectionTimeout());
    apiClient.setReadTimeout(options.getReadTimeout());
    apiClient.setWriteTimeout(options.getWriteTimeout());
    apiClient.setDebugging(log.isDebugEnabled());
    apiClient.setUserAgent("JavaSDK " + io.harness.cf.Version.VERSION);
    // if http client response is 403 we need to reauthenticate
    apiClient
        .getHttpClient()
        .newBuilder()
        .addInterceptor(
            chain -> {
              final Request request = chain.request();
              // if you need to do something before request replace this
              // comment with code
              Response response = chain.proceed(request);
              if (response.code() == 403 && onUnauthorized != null) {
                onUnauthorized.run();
              }
              return response;
            })
        .addInterceptor(new RetryInterceptor(3, 2000));
    return apiClient;
  }

  protected ApiClient makeMetricsApiClient() {
    final int maxTimeout = 30 * 60 * 1000;
    final ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(options.getEventUrl());
    apiClient.setConnectTimeout(maxTimeout);
    apiClient.setReadTimeout(maxTimeout);
    apiClient.setWriteTimeout(maxTimeout);
    apiClient.setDebugging(log.isDebugEnabled());
    apiClient.getHttpClient().newBuilder().addInterceptor(new RetryInterceptor(3, 2000));
    return apiClient;
  }

  @Override
  public String authenticate() throws ConnectorException {
    try {
      final AuthenticationRequest request = AuthenticationRequest.builder().apiKey(apiKey).build();
      final AuthenticationResponse response = api.authenticate(request);
      token = response.getAuthToken();
      processToken(token);
      return token;
    } catch (ApiException apiException) {
      log.error("Failed to get auth token {}", apiException.getMessage());
      if (apiException.getCode() == 401 || apiException.getCode() == 403) {
        String errorMsg = String.format("Invalid apiKey %s. Serving default value. ", apiKey);
        log.error(errorMsg);
        throw new ConnectorException(errorMsg);
      }
      throw new ConnectorException(apiException.getMessage());
    }
  }

  @Override
  public void setOnUnauthorized(Runnable runnable) {
    onUnauthorized = runnable;
  }

  protected void processToken(@NonNull final String token) {
    api.getApiClient().addDefaultHeader("Authorization", String.format("Bearer %s", token));
    metricsApi.getApiClient().addDefaultHeader("Authorization", "Bearer " + token);

    // get claims
    int i = token.lastIndexOf('.');
    String unsignedJwt = token.substring(0, i + 1);
    Jwt<?, Claims> untrusted = Jwts.parserBuilder().build().parseClaimsJwt(unsignedJwt);

    environment = (String) untrusted.getBody().get("environment");
    cluster = (String) untrusted.getBody().get("clusterIdentifier");
  }

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {
    try {
      return api.getFeatureConfig(environment, cluster);
    } catch (ApiException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public FeatureConfig getFlag(@NonNull final String identifier) throws ConnectorException {
    try {
      return api.getFeatureConfigByIdentifier(identifier, environment, cluster);
    } catch (ApiException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    try {
      return api.getAllSegments(environment, cluster);
    } catch (ApiException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public Segment getSegment(@NonNull final String identifier) throws ConnectorException {
    try {
      return api.getSegmentByIdentifier(identifier, environment, cluster);
    } catch (ApiException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public void postMetrics(@NonNull final Metrics metrics) throws ConnectorException {
    try {
      metricsApi.postMetrics(environment, cluster, metrics);
    } catch (ApiException e) {
      throw new ConnectorException(e.getMessage());
    }
  }

  @Override
  public Service stream(@NonNull final Updater updater) {
    if (eventSource != null) {
      eventSource.close();
      eventSource = null;
    }
    final String sseUrl = String.join("", options.getConfigUrl(), "/stream?cluster=" + cluster);
    final Map<String, String> map = new HashMap<>();
    map.put("Authorization", "Bearer " + token);
    map.put("API-Key", apiKey);
    eventSource = new EventSource(sseUrl, map, updater);
    return eventSource;
  }

  @Override
  public void close() {
    api.getApiClient().getHttpClient().connectionPool().evictAll();
    metricsApi.getApiClient().getHttpClient().connectionPool().evictAll();
    eventSource.close();
  }
}
