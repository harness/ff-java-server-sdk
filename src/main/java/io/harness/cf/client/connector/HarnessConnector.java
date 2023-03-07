package io.harness.cf.client.connector;

import com.google.gson.Gson;
import io.harness.cf.ApiClient;
import io.harness.cf.ApiException;
import io.harness.cf.api.ClientApi;
import io.harness.cf.api.MetricsApi;
import io.harness.cf.client.dto.Claim;
import io.harness.cf.client.logger.LogUtil;
import io.harness.cf.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
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

  static {
    LogUtil.setSystemProps();
  }

  public HarnessConnector(@NonNull String apiKey) {
    this(apiKey, HarnessConfig.builder().build());
  }

  public HarnessConnector(@NonNull final String apiKey, @NonNull final HarnessConfig options) {
    this.apiKey = apiKey;
    this.options = options;
    this.api = new ClientApi(makeApiClient(2000));
    this.metricsApi = new MetricsApi(makeMetricsApiClient(2000));
    log.info("Connector initialized, with options " + options);
  }

  @SneakyThrows
  private byte[] certToByteArray(X509Certificate cert) {
    return cert.getEncoded();
  }

  ApiClient makeApiClient(int retryBackOfDelay) {
    final ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(options.getConfigUrl());
    apiClient.setConnectTimeout(options.getConnectionTimeout());
    apiClient.setReadTimeout(options.getReadTimeout());
    apiClient.setWriteTimeout(options.getWriteTimeout());
    apiClient.setDebugging(log.isDebugEnabled());
    apiClient.setUserAgent("JavaSDK " + io.harness.cf.Version.VERSION);

    setupTls(apiClient);

    // if http client response is 403 we need to reauthenticate
    apiClient.setHttpClient(
        apiClient
            .getHttpClient()
            .newBuilder()
            .addInterceptor(this::interceptor)
            .addInterceptor(new RetryInterceptor(3, retryBackOfDelay))
            .build());
    log.info("apiClient definition complete");
    return apiClient;
  }

  private Response interceptor(Interceptor.Chain chain) throws IOException {
    final Request request =
        chain.request().newBuilder().addHeader("X-Request-ID", getRequestID()).build();
    log.info("interceptor: requesting url {}", request.url().url());

    Response response = chain.proceed(request);

    if (response.code() == 403 && onUnauthorized != null) {
      onUnauthorized.run();
    }

    return response;
  }

  ApiClient makeMetricsApiClient(int retryBackoffDelay) {
    final int maxTimeout = 30 * 60 * 1000;
    final ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(options.getEventUrl());
    apiClient.setConnectTimeout(maxTimeout);
    apiClient.setReadTimeout(maxTimeout);
    apiClient.setWriteTimeout(maxTimeout);
    apiClient.setDebugging(log.isDebugEnabled());
    apiClient.setUserAgent("JavaSDK " + io.harness.cf.Version.VERSION);

    setupTls(apiClient);

    apiClient.setHttpClient(
        apiClient
            .getHttpClient()
            .newBuilder()
            .addInterceptor(this::metricsInterceptor)
            .addInterceptor(new RetryInterceptor(3, retryBackoffDelay))
            .build());
    log.info("metricsApiClient definition complete");
    return apiClient;
  }

  private Response metricsInterceptor(Interceptor.Chain chain) throws IOException {
    final Request request =
        chain.request().newBuilder().addHeader("X-Request-ID", getRequestID()).build();
    log.info("metrics interceptor: requesting url {}", request.url().url());

    return chain.proceed(request);
  }

  protected String getRequestID() {
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
    try {
      final AuthenticationRequest request = AuthenticationRequest.builder().apiKey(apiKey).build();
      final AuthenticationResponse response = api.authenticate(request);
      log.info("Successfully authenticated");
      token = response.getAuthToken();
      log.debug("Token generated");
      processToken(token);
      return token;
    } catch (ApiException apiException) {
      if (apiException.getCode() == 401 || apiException.getCode() == 403) {
        String errorMsg = String.format("Invalid apiKey %s. SDK will serve default values", apiKey);
        log.error(errorMsg);
        throw new ConnectorException(errorMsg, apiException.getCode(), apiException.getMessage());
      }
      log.error("Failed to get auth token", apiException);
      throw new ConnectorException(
          apiException.getMessage(), apiException.getCode(), apiException.getMessage());
    } catch (Throwable ex) {
      log.error("Unexpected exception", ex);
      throw ex;
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
    log.debug("Authorization header added to apiClient");
    metricsApi.getApiClient().addDefaultHeader(authorizationKey, bearerToken);
    log.debug("Authorization header added to metricsApi");
    // get claims
    String decoded =
        new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

    Claim claim = gson.fromJson(decoded, Claim.class);
    log.debug("Claims successfully parsed from decoded payload");
    environment = claim.getEnvironment();
    cluster = claim.getClusterIdentifier();
    log.info("Token successfully processed, environment {}, cluster {}", environment, cluster);
  }

  @Override
  public List<FeatureConfig> getFlags() throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.info("Fetching flags on env {} and cluster {}", this.environment, this.cluster);
    List<FeatureConfig> featureConfig = new ArrayList<>();
    try {
      featureConfig = api.getFeatureConfig(environment, cluster);
      log.info(
          "Total configurations fetched: {} on env {} and cluster {}",
          featureConfig.size(),
          this.environment,
          this.cluster);
      if (log.isTraceEnabled()) {
        log.trace("Got the following features: " + featureConfig);
      }
      return featureConfig;
    } catch (ApiException e) {
      log.error(
          "Exception was raised while fetching the flags on env {} and cluster {}",
          this.environment,
          this.cluster,
          e);
      throw new ConnectorException(e.getMessage(), e.getCode(), e.getMessage());
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }

  @Override
  public FeatureConfig getFlag(@NonNull final String identifier) throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.debug(
        "Fetch flag {} from env {} and cluster {}", identifier, this.environment, this.cluster);
    try {
      FeatureConfig featureConfigByIdentifier =
          api.getFeatureConfigByIdentifier(identifier, environment, cluster);
      log.debug(
          "Flag {} successfully fetched from env {} and cluster {}",
          identifier,
          this.environment,
          this.cluster);
      return featureConfigByIdentifier;
    } catch (ApiException e) {
      log.error(
          "Exception was raised while fetching the flag {} on env {} and cluster {}",
          identifier,
          this.environment,
          this.cluster,
          e);
      throw new ConnectorException(e.getMessage(), e.getCode(), e.getMessage());
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }

  @Override
  public List<Segment> getSegments() throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.debug(
        "Fetching target groups on environment {} and cluster {}", this.environment, this.cluster);
    List<Segment> allSegments = new ArrayList<>();
    try {
      allSegments = api.getAllSegments(environment, cluster);
      log.debug(
          "Total target groups fetched: {} on env {} and cluster {}",
          allSegments.size(),
          this.environment,
          this.cluster);
      return allSegments;
    } catch (ApiException e) {
      log.error(
          "Exception was raised while fetching the target groups on env {} and cluster {} : httpCode={} message={}",
          this.environment,
          this.cluster,
          e.getCode(),
          e.getMessage(),
          e);
      throw new ConnectorException(e.getMessage(), e.getCode(), e.getMessage());
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }

  @Override
  public Segment getSegment(@NonNull final String identifier) throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.debug(
        "Fetching the target group {} on environment {} and cluster {}",
        identifier,
        this.environment,
        this.cluster);
    try {
      Segment segmentByIdentifier = api.getSegmentByIdentifier(identifier, environment, cluster);
      log.debug(
          "Segment {} successfully fetched from env {} and cluster {}",
          identifier,
          this.environment,
          this.cluster);
      return segmentByIdentifier;
    } catch (ApiException e) {
      log.error(
          "Exception was raised while fetching the target group {} on env {} and cluster {}",
          identifier,
          this.environment,
          this.cluster,
          e);
      throw new ConnectorException(e.getMessage(), e.getCode(), e.getMessage());
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }

  @Override
  public void postMetrics(@NonNull final Metrics metrics) throws ConnectorException {
    final String requestId = UUID.randomUUID().toString();
    MDC.put(REQUEST_ID_KEY, requestId);
    log.debug("Uploading metrics on environment {} and cluster {}", this.environment, this.cluster);
    try {
      metricsApi.postMetrics(environment, cluster, metrics);
      log.debug(
          "Metrics uploaded successfully on environment {} and cluster {}",
          this.environment,
          this.cluster);
    } catch (ApiException e) {
      log.error(
          "Exception was raised while uploading metrics on env {} and cluster {}",
          this.environment,
          this.cluster,
          e);
      throw new ConnectorException(e.getMessage(), e.getCode(), e.getMessage());
    } finally {
      MDC.remove(REQUEST_ID_KEY);
    }
  }

  @Override
  public Service stream(@NonNull final Updater updater) {
    log.debug("Check if eventsource is already initialized");
    if (eventSource != null) {
      log.debug("EventSource is already initialized, closing ...");
      eventSource.close();
      eventSource = null;
    }
    final String sseUrl = String.join("", options.getConfigUrl(), "/stream?cluster=" + cluster);
    final Map<String, String> map = new HashMap<>();
    map.put("Authorization", "Bearer " + token);
    map.put("API-Key", apiKey);
    log.info("Initialize new EventSource instance");
    eventSource =
        new EventSource(
            sseUrl,
            map,
            updater,
            Math.max(options.getSseReadTimeout(), 1),
            2_000,
            options.getTlsTrustedCAs());
    return eventSource;
  }

  @Override
  public void close() {
    log.debug("closing connector");
    api.getApiClient().getHttpClient().connectionPool().evictAll();
    log.debug("All apiClient connections evicted");
    metricsApi.getApiClient().getHttpClient().connectionPool().evictAll();
    log.debug("All metricsApiClient connections evicted");
    if (eventSource != null) {
      eventSource.close();
    }
    log.debug("connector closed!");
  }

  private void setupTls(ApiClient apiClient) {
    final List<X509Certificate> trustedCAs = options.getTlsTrustedCAs();
    if (trustedCAs != null && !trustedCAs.isEmpty()) {

      // because openapi doesn't take X509 certs directly we need some boilerplate
      byte[] certsAsBytes =
          trustedCAs.stream()
              .map(this::certToByteArray)
              .collect(ByteArrayOutputStream::new, (s, b) -> s.write(b, 0, b.length), (a, b) -> {})
              .toByteArray();

      apiClient.setSslCaCert(new ByteArrayInputStream(certsAsBytes));
    }
  }

  /* package private - should not be used outside of tests */

  HarnessConnector(
      @NonNull final String apiKey, @NonNull final HarnessConfig options, int retryBackOffDelay) {
    this.apiKey = apiKey;
    this.options = options;
    this.api = new ClientApi(makeApiClient(retryBackOffDelay));
    this.metricsApi = new MetricsApi(makeMetricsApiClient(retryBackOffDelay));
    log.info(
        "Connector initialized, with options "
            + options
            + " and retry backoff delay "
            + retryBackOffDelay);
  }
}
