package io.harness.cf.client.api;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import io.harness.cf.ApiClient;
import io.harness.cf.api.ClientApi;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
class InnerClient
    implements AutoCloseable,
        FlagEvaluateCallback,
        AuthCallback,
        PollerCallback,
        StreamCallback,
        RepositoryCallback,
        MetricsCallback {

  enum Processor {
    POLL,
    STREAM,
    METRICS,
  }

  private Evaluation evaluator;
  private Repository repository;
  private ClientApi api;
  private Config options;
  private AuthService authService;
  private PollingProcessor pollProcessor;
  private StreamProcessor streamProcessor;
  private MetricsProcessor metricsProcessor;
  private boolean initialized = false;
  private boolean closing = false;
  private boolean failure = false;
  private boolean pollerReady = false;
  private boolean streamReady = false;
  private boolean metricReady = false;

  private final ConcurrentHashMap<Event, CopyOnWriteArrayList<Consumer<String>>> events =
      new ConcurrentHashMap<>();

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
    metricsProcessor = new MetricsProcessor(this.options, this);

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
            })
        .addInterceptor(new RetryInterceptor(3, 2000));
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
    metricsProcessor.setToken(token);
  }

  protected void onUnauthorized() {
    if (closing) {
      return;
    }
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
    if (closing) {
      return;
    }
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
  }

  @Override
  public void onPollerReady() {
    initialize(Processor.POLL);
  }

  @Override
  public void onPollerError(@NonNull String error) {
    failure = true;
  }

  @Override
  public void onStreamConnected() {
    pollProcessor.stop();
  }

  @Override
  public void onStreamDisconnected() {
    if (!closing) {
      pollProcessor.start();
    }
  }

  @Override
  public void onStreamReady() {
    initialize(Processor.STREAM);
  }

  @Override
  public void onStreamError() {}

  @Override
  public void onFlagStored(@NonNull String identifier) {
    notifyConsumers(Event.CHANGED, identifier);
  }

  @Override
  public void onFlagDeleted(@NonNull String identifier) {
    notifyConsumers(Event.CHANGED, identifier);
  }

  @Override
  public void onSegmentStored(@NonNull String identifier) {
    notifyConsumers(Event.CHANGED, identifier);
  }

  @Override
  public void onSegmentDeleted(@NonNull String identifier) {
    notifyConsumers(Event.CHANGED, identifier);
  }

  @Override
  public void onMetricsReady() {
    initialize(Processor.METRICS);
  }

  @Override
  public void onMetricsError(@NonNull String error) {}

  @Override
  public void onMetricsFailure() {
    failure = true;
  }

  private synchronized void initialize(Processor processor) {
    if (closing) {
      return;
    }
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
    notify();
    log.info("Initialization is complete");

    notifyConsumers(Event.READY, null);
  }

  protected void notifyConsumers(@NonNull Event event, String value) {
    CopyOnWriteArrayList<Consumer<String>> consumers = events.get(event);
    if (consumers != null && !consumers.isEmpty()) {
      for (Consumer<String> consumer : consumers) {
        consumer.accept(value);
      }
    }
  }

  /** if waitForInitialization is used then on(READY) will never be triggered */
  public synchronized void waitForInitialization() throws InterruptedException {
    if (!initialized) {
      log.info("Wait for initialization to finish");
      wait();
    }
  }

  public void on(@NonNull Event event, @NonNull Consumer<String> consumer) {
    final CopyOnWriteArrayList<Consumer<String>> consumers =
        events.getOrDefault(event, new CopyOnWriteArrayList<>());
    consumers.add(consumer);
    events.put(event, consumers);
  }

  public void off() {
    events.clear();
  }

  public void off(@NonNull Event event) {
    events.get(event).clear();
  }

  public void off(@NonNull Event event, @NonNull Consumer<String> consumer) {
    events.get(event).removeIf(next -> next == consumer);
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
      @NonNull FeatureConfig featureConfig, Target target, @NonNull Variation variation) {
    metricsProcessor.pushToQueue(target, featureConfig, variation);
  }

  public void close() {
    log.info("Closing the client");
    closing = true;
    off();
    authService.close();
    repository.close();
    streamProcessor.close();
    pollProcessor.close();
    metricsProcessor.close();
  }
}
