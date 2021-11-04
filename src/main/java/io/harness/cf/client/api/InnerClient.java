package io.harness.cf.client.api;

import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.gson.JsonObject;
import io.harness.cf.ApiClient;
import io.harness.cf.api.ClientApi;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
class InnerClient
    implements FlagEvaluateCallback,
        AuthCallback,
        PollerCallback,
        StreamCallback,
        RepositoryCallback {

  public enum Event {
    READY,
    FAILED,
    CHANGED
  }

  enum Processor {
    POLL,
    STREAM,
    METRICS,
  }

  private Evaluation evaluator;
  private Repository repository;
  private ClientApi api;
  private Config options;
  private final EventBus eventBus = new EventBus();
  private AuthService authService;
  private PollingProcessor pollProcessor;
  private StreamProcessor streamProcessor;
  private MetricsProcessor metricsProcessor;
  private boolean initialized = false;
  private boolean failure = false;
  private boolean pollerReady = false;
  private boolean streamReady = false;
  private boolean metricReady = false;

  public InnerClient(@NonNull final String sdkKey) {
    this(sdkKey, Config.builder().build());
  }

  public InnerClient(@NonNull final String sdkKey, final Config options) {
    if (Strings.isNullOrEmpty(sdkKey)) {
      log.error("SDK key cannot be empty!");
      return;
    }
    this.options = options;

    // initialization
    api = new ClientApi(makeApiClient());
    repository = new StorageRepository(options.getCache(), options.getStore(), this);
    evaluator = new Evaluator(repository);
    authService = new AuthService(api, sdkKey, options.getPollIntervalInSeconds(), this);
    pollProcessor = new PollingProcessor(api, repository, options.getPollIntervalInSeconds(), this);
    streamProcessor = new StreamProcessor(sdkKey, api, repository, options.getConfigUrl(), this);
    metricsProcessor = new MetricsProcessor();

    // start with authentication
    authService.startAsync();
  }

  protected ApiClient makeApiClient() {
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(options.getConfigUrl());
    apiClient.setConnectTimeout(options.getConnectionTimeout());
    apiClient.setReadTimeout(options.getReadTimeout());
    apiClient.setWriteTimeout(options.getWriteTimeout());
    apiClient.setDebugging(log.isDebugEnabled());
    apiClient.setUserAgent("java " + io.harness.cf.Version.VERSION);
    // if http client response is 403 we need to reauthenticate
    apiClient
        .getHttpClient()
        .newBuilder()
        .addInterceptor(
            chain -> {
              Request request = chain.request();
              // if you need to do something before request replace this
              // comment with code
              Response response = chain.proceed(request);
              if (response.code() == 403) {
                onUnauthorized();
              }
              return response;
            });
    return apiClient;
  }

  protected void processToken(String token) {
    api.getApiClient().addDefaultHeader("Authorization", String.format("Bearer %s", token));

    // get claims
    int i = token.lastIndexOf('.');
    String unsignedJwt = token.substring(0, i + 1);
    Jwt<?, Claims> untrusted = Jwts.parserBuilder().build().parseClaimsJwt(unsignedJwt);

    String environment = (String) untrusted.getBody().get("environment");
    String cluster = (String) untrusted.getBody().get("clusterIdentifier");

    // set values to processors
    pollProcessor.setEnvironment(environment);
    pollProcessor.setCluster(cluster);

    streamProcessor.setEnvironment(environment);
    streamProcessor.setCluster(cluster);
    streamProcessor.setToken(token);

    metricsProcessor.setEnvironmentID(environment);
    metricsProcessor.setCluster(cluster);
  }

  protected void onUnauthorized() {
    authService.startAsync();
    pollProcessor.stop();
    if (options.isStreamEnabled()) {
      streamProcessor.stop();
    }

    if (options.isAnalyticsEnabled()) {
      metricsProcessor.stop();
    }
  }

  @Override
  public void onAuthSuccess(@NonNull final String token) {
    log.info("SDK successfully logged in");
    processToken(token);
    // run services only after token is processed
    pollProcessor.start();
    if (options.isStreamEnabled()) {
      streamProcessor.start();
    }

    if (options.isAnalyticsEnabled()) {
      metricsProcessor.start();
    }
  }

  @Override
  public void onAuthError(String error) {
    failure = true;
    eventBus.post(new CustomEvent<>(Event.FAILED));
  }

  @Override
  public void onPollerReady() {
    initialize(Processor.POLL);
  }

  @Override
  public void onPollerError(@NonNull String error) {
    failure = true;
    eventBus.post(new CustomEvent<>(Event.FAILED));
  }

  @Override
  public void onStreamConnected() {
    pollProcessor.stop();
  }

  @Override
  public void onStreamDisconnected() {
    pollProcessor.start();
  }

  @Override
  public void onStreamError() {}

  @Override
  public void onFlagStored(@NonNull String identifier) {}

  @Override
  public void onFlagDeleted(@NonNull String identifier) {}

  @Override
  public void onSegmentStored(@NonNull String identifier) {}

  @Override
  public void onSegmentDeleted(@NonNull String identifier) {}

  private void initialize(Processor processor) {
    switch (processor) {
      case POLL:
        pollerReady = true;
        log.debug("PollingProcessor ready");
        break;
      case STREAM:
        streamReady = true;
        log.debug("StreamingProcessor ready");
        break;
      case METRICS:
        metricReady = true;
        log.debug("MetricsProcessor ready");
        break;
    }

    if (options.isStreamEnabled() && !streamReady) {
      return;
    }

    if (options.isAnalyticsEnabled() && !this.metricReady) {
      return;
    }

    if (!this.pollerReady) {
      return;
    }

    initialized = true;
    eventBus.post(new CustomEvent<>(Event.READY));
    notify();
  }

  public void waitForInitialization() throws InterruptedException {
    while (!initialized) {
      wait();
    }
  }

  public boolean boolVariation(@NonNull String identifier, Target target, boolean defaultValue) {
    return evaluator.boolVariation(identifier, target, defaultValue, this);
  }

  public String stringVariation(
      @NonNull String identifier, Target target, @NonNull String defaultValue) {
    return evaluator.stringVariation(identifier, target, defaultValue, this);
  }

  public double numberVariation(@NonNull String identifier, Target target, double defaultValue) {
    return evaluator.numberVariation(identifier, target, defaultValue, this);
  }

  public JsonObject jsonVariation(
      @NonNull String identifier, Target target, @NonNull JsonObject defaultValue) {
    return evaluator.jsonVariation(identifier, target, defaultValue, this);
  }

  @Override
  public void processEvaluation(
      @NonNull FeatureConfig featureConfig, Target target, @NonNull Variation variation) {}

  public void close() {
    log.info("Closing the client");
    repository.close();
  }
}
