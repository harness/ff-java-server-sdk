package io.harness.cf.client.connector;

import com.google.gson.Gson;
import io.harness.cf.ApiClient;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.api.MetricsApi;
import io.harness.cf.client.dto.Claim;
import io.harness.cf.model.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.MDC;

@Slf4j
public class HarnessConnector implements Connector, AutoCloseable {
  public static final String REQUEST_ID_KEY = "requestId";
  private final ClientApi api;
  private final MetricsApi metricsApi;
  private final String apiKey;
  private final HarnessConfig options;

  private String token;
  private String environment;
  private String cluster;

  private EventSource eventSource;
  private Runnable onUnauthorized;

  private final Gson gson = new Gson();

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
    apiClient.setHttpClient(
        apiClient
            .getHttpClient()
            .newBuilder()
            .addInterceptor(
                chain -> {
                  final Request request =
                      chain
                          .request()
                          .newBuilder()
                          .addHeader("X-Request-ID", getRequestID())
                          .build();
                  log.info("requesting url {}", request.url().url());
                  Response response = chain.proceed(request);
                  if (response.code() == 403 && onUnauthorized != null) {
                    onUnauthorized.run();
                  }

                  return response;
                })
            .addInterceptor(new RetryInterceptor(3, 2000))
            .build());

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
    apiClient.setHttpClient(
        apiClient
            .getHttpClient()
            .newBuilder()
            .addInterceptor(
                chain -> {
                  final Request request =
                      chain
                          .request()
                          .newBuilder()
                          .addHeader("X-Request-ID", getRequestID())
                          .build();
                  return chain.proceed(request);
                })
            .addInterceptor(new RetryInterceptor(3, 2000))
            .build());
    return apiClient;
  }

  protected String getRequestID() {
    log.debug("run getRequestID");
    String requestId = MDC.get(REQUEST_ID_KEY);
    if (requestId == null) {
      requestId = "";
    }
    return requestId;
  }

  @Override
  public String authenticate() throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.info("run authenticate");
    try {
      final AuthenticationRequest request = AuthenticationRequest.builder().apiKey(apiKey).build();
      final AuthenticationResponse response = api.authenticate(request);
      log.info("authenticate -> successfully authenticated");
      token = response.getAuthToken();
      log.debug("authenticate -> token generated");
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
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }

  @Override
  public void setOnUnauthorized(Runnable runnable) {
    onUnauthorized = runnable;
  }

  protected void processToken(@NonNull final String token) {
    final String authorizationKey = "Authorization";
    final String bearerToken = "Bearer " + token;
    api.getApiClient().addDefaultHeader(authorizationKey, bearerToken);
    metricsApi.getApiClient().addDefaultHeader(authorizationKey, bearerToken);

    // get claims
    String decoded =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

    Claim claim = gson.fromJson(decoded, Claim.class);

    environment = claim.getEnvironment();
    cluster = claim.getClusterIdentifier();
  }

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.info("run getFlags");
    try {
      List<FeatureConfig> featureConfig = api.getFeatureConfig(environment, cluster);
      log.info("getFlags -> total flags fetched: {}", featureConfig.size());
      return featureConfig;
    } catch (ApiException e) {
      log.info("getFlags raised exception {}", e.getMessage());
      throw new ConnectorException(e.getMessage());
    } finally {
      MDC.remove(REQUEST_ID_KEY);
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
    log.info("poll segments from server");
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
    if (eventSource != null) {
      eventSource.close();
    }
  }
}
