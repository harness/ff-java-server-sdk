package io.harness.cf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.*;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.FeatureSnapshot;
import io.harness.cf.model.Variation;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class InnerClient
    implements AutoCloseable,
        FlagEvaluateCallback,
        AuthCallback,
        PollerCallback,
        RepositoryCallback,
        MetricsCallback,
        Updater {

  enum Processor {
    POLL,
    STREAM,
    METRICS,
  }

  private Connector connector;
  private Evaluation evaluator;
  private Repository repository;
  private BaseConfig options;
  private AuthService authService;
  private PollingProcessor pollProcessor;
  private MetricsProcessor metricsProcessor;
  private UpdateProcessor updateProcessor;
  private boolean initialized = false;
  private boolean closing = false;
  private boolean failure = false;
  private boolean pollerReady = false;
  private boolean streamReady = false;
  private boolean metricReady = false;

  private final ConcurrentHashMap<Event, CopyOnWriteArrayList<Consumer<String>>> events =
      new ConcurrentHashMap<>();

  public InnerClient(@NonNull final String sdkKey) {
    this(sdkKey, BaseConfig.builder().build());
  }

  @Deprecated
  public InnerClient(@NonNull final String sdkKey, @NonNull final Config options) {
    HarnessConfig config =
        HarnessConfig.builder()
            .configUrl(options.getConfigUrl())
            .eventUrl(options.getEventUrl())
            .connectionTimeout(options.getConnectionTimeout())
            .readTimeout(options.readTimeout)
            .writeTimeout(options.getWriteTimeout())
            .build();
    HarnessConnector harnessConnector = new HarnessConnector(sdkKey, config);
    setUp(harnessConnector, options);
  }

  public InnerClient(@NonNull final String sdkKey, @NonNull final BaseConfig options) {
    HarnessConfig config = HarnessConfig.builder().build();
    HarnessConnector harnessConnector = new HarnessConnector(sdkKey, config);
    setUp(harnessConnector, options);
  }

  public InnerClient(@NonNull final Connector connector) {
    this(connector, BaseConfig.builder().build());
  }

  public InnerClient(@NonNull Connector connector, @NonNull final BaseConfig options) {
    setUp(connector, options);
  }

  protected void setUp(@NonNull final Connector connector, @NonNull final BaseConfig options) {
    this.options = options;
    log.info("Starting SDK client with configuration: {}", this.options);
    this.connector = connector;
    this.connector.setOnUnauthorized(this::onUnauthorized);

    // initialization
    repository = new StorageRepository(options.getCache(), options.getStore(), this, false);
    evaluator = new Evaluator(repository, options);
    authService = new AuthService(this.connector, options.getPollIntervalInSeconds(), this);
    pollProcessor =
        new PollingProcessor(this.connector, repository, options.getPollIntervalInSeconds(), this);
    metricsProcessor = new MetricsProcessor(this.connector, this.options, this);
    updateProcessor = new UpdateProcessor(this.connector, this.repository, this);

    // start with authentication
    authService.start();
  }

  protected void onUnauthorized() {
    if (closing) {
      return;
    }
    log.info("Unauthorized event received. Stopping all processors and run auth service");
    pollProcessor.stop();
    if (options.isStreamEnabled()) {
      updateProcessor.stop();
    }

    if (options.isAnalyticsEnabled()) {
      metricsProcessor.stop();
    }

    authService.start();

    log.info("re-auth started");
  }

  @Override
  public void onAuthSuccess() {
    log.debug("SDK successfully logged in");
    if (closing) {
      return;
    }

    log.debug("start poller processor");
    pollProcessor.start();

    if (options.isStreamEnabled()) {
      log.debug("Stream enabled, start update processor");
      updateProcessor.start();
    }

    if (options.isAnalyticsEnabled()) {
      log.debug("Analytics enabled, start metrics processor");
      metricsProcessor.start();
    }
  }

  @Override
  public void onPollerReady() {
    initialize(Processor.POLL);
  }

  @Override
  public void onPollerError(@NonNull final Exception exc) {
    log.error("PollerProcessor exception", exc);
  }

  @Override
  public void onPollerFailed(@NonNull final Exception exc) {
    log.error("PollerProcessor failed while initializing, exception: ", exc);
  }

  @Override
  public void onFlagStored(@NonNull final String identifier) {
    notifyConsumers(Event.CHANGED, identifier);
  }

  @Override
  public void onFlagDeleted(@NonNull final String identifier) {
    notifyConsumers(Event.CHANGED, identifier);
  }

  @Override
  public void onSegmentStored(@NonNull final String identifier) {
    repository.findFlagsBySegment(identifier).forEach(s -> notifyConsumers(Event.CHANGED, s));
  }

  @Override
  public void onSegmentDeleted(@NonNull final String identifier) {
    repository.findFlagsBySegment(identifier).forEach(s -> notifyConsumers(Event.CHANGED, s));
  }

  @Override
  public void onMetricsReady() {
    initialize(Processor.METRICS);
  }

  @Override
  public void onMetricsError(@NonNull final String error) {
    log.error("Metrics error: {}", error);
  }

  @Override
  public synchronized void onMetricsFailure() {
    failure = true;
    notifyAll();
  }

  @Override
  public void onConnected() {
    SdkCodes.infoStreamConnected();

    if (pollProcessor.isRunning()) {
      // refresh any flags that may have gotten out of sync if the SSE connection was down
      pollProcessor.retrieveAll();
      pollProcessor.stop();
    }
  }

  @Override
  public void onDisconnected(String reason) {

    SdkCodes.warnStreamDisconnected(reason);

    if (!closing && !pollProcessor.isRunning()) {
      log.debug("onDisconnected triggered, starting poller to get latest flags");

      pollProcessor.close();
      pollProcessor =
          new PollingProcessor(connector, repository, options.getPollIntervalInSeconds(), this);
      pollProcessor.start();

      if (updateProcessor != null && options.isStreamEnabled()) {
        updateProcessor.restart();
      }

    } else {
      log.debug(
          "Poller already running [closing={} interval={}]",
          closing,
          options.getPollIntervalInSeconds());
      log.debug("SSE disconnect detected - asking poller to refresh flags");
      pollProcessor.retrieveAll();
    }
  }

  @Override
  public void onReady() {
    log.debug("onReady triggered");
    initialize(Processor.STREAM);
  }

  public synchronized void onFailure(@NonNull final String error) {
    SdkCodes.warnAuthFailedSrvDefaults(error);
    failure = true;
    notifyAll();
  }

  @Override
  public void update(@NonNull final Message message) {
    log.debug("update triggered [event={}] ", message.getEvent());
    updateProcessor.update(message);
  }

  public void update(@NonNull final Message message, final boolean manual) {
    log.debug("update triggered [event={} manual={}] ", message.getEvent(), manual);
    if (options.isStreamEnabled() && manual) {
      log.warn(
          "You have run update method manually with the stream enabled. Please turn off the stream in this case.");
    }
    update(message);
  }

  private synchronized void initialize(@NonNull final Processor processor) {
    if (initialized || closing) {
      log.debug("client is already initialized {} or closing {}", initialized, closing);
      return;
    }
    switch (processor) {
      case POLL:
        pollerReady = true;
        log.debug("PollingProcessor ready");
        break;
      case STREAM:
        streamReady = true;
        log.debug("Updater ready");
        break;
      case METRICS:
        metricReady = true;
        log.debug("MetricsProcessor ready");
        break;
    }

    if ((options.isStreamEnabled() && !streamReady)
        || (options.isAnalyticsEnabled() && !this.metricReady)
        || (!this.pollerReady)) {
      return;
    }

    initialized = true;
    notifyAll();
    notifyConsumers(Event.READY, null);
    SdkCodes.infoSdkInitOk();
  }

  protected void notifyConsumers(@NonNull final Event event, final String value) {
    CopyOnWriteArrayList<Consumer<String>> consumers = events.get(event);
    if (consumers != null && !consumers.isEmpty()) {
      consumers.forEach(c -> c.accept(value));
    }
  }

  /** if waitForInitialization is used then on(READY) will never be triggered */
  public synchronized void waitForInitialization()
      throws InterruptedException, FeatureFlagInitializeException {
    while (!initialized) {
      log.info("Wait for initialization to finish");
      wait(5000);

      if (failure) {
        log.error("Failure while initializing SDK!");
        throw new FeatureFlagInitializeException();
      }
    }
  }

  public List<FeatureSnapshot> getFeatureSnapshots() {
    // TODO return null/empty list if snapshot is not enabled in config.
    return getFeatureSnapshots("");
  }

  public List<FeatureSnapshot> getFeatureSnapshots(String prefix) {
    // TODO return null/empty list if snapshot is not enabled in config.
    List<String> identifiers = repository.getAllFeatureIdentifiers(prefix);
    List<FeatureSnapshot> snapshots = new LinkedList<>();

    for (String identifier : identifiers) {
      FeatureSnapshot snapshot = getFeatureSnapshot(identifier);
      snapshots.add(snapshot);
    }

    return snapshots;
  }

  public FeatureSnapshot getFeatureSnapshot(@NonNull String identifier) {
    // TODO return null/empty list if snapshot is not enabled in config.
    Optional<FeatureConfig[]> ofc = repository.getCurrentAndPreviousFeatureConfig(identifier);
    FeatureSnapshot result = new FeatureSnapshot();
    if (ofc.isPresent()) {
      FeatureConfig[] fc = ofc.get();
      result.setPrevious(fc[0]);
      result.setCurrent(fc[1]);
    }
    // this is here to create a deep copy of the object before its returned.
    // this way we protect the cache.
    Gson gson = new Gson();
    FeatureSnapshot deepCopySnapshot = gson.fromJson(gson.toJson(result), FeatureSnapshot.class);
    return deepCopySnapshot;
  }

  public void on(@NonNull final Event event, @NonNull final Consumer<String> consumer) {
    final CopyOnWriteArrayList<Consumer<String>> consumers =
        events.getOrDefault(event, new CopyOnWriteArrayList<>());
    consumers.add(consumer);
    events.put(event, consumers);
  }

  public void off() {
    events.clear();
  }

  public void off(@NonNull final Event event) {
    events.get(event).clear();
  }

  public void off(@NonNull final Event event, @NonNull final Consumer<String> consumer) {
    events.get(event).removeIf(next -> next == consumer);
  }

  public boolean boolVariation(
      @NonNull final String identifier, final Target target, final boolean defaultValue) {
    return evaluator.boolVariation(identifier, target, defaultValue, this);
  }

  public String stringVariation(
      @NonNull final String identifier, final Target target, @NonNull final String defaultValue) {
    return evaluator.stringVariation(identifier, target, defaultValue, this);
  }

  public double numberVariation(
      @NonNull final String identifier, final Target target, final double defaultValue) {
    return evaluator.numberVariation(identifier, target, defaultValue, this);
  }

  public JsonObject jsonVariation(
      @NonNull String identifier, Target target, @NonNull JsonObject defaultValue) {
    return evaluator.jsonVariation(identifier, target, defaultValue, this);
  }

  @Override
  public void processEvaluation(
      @NonNull FeatureConfig featureConfig, Target target, @NonNull Variation variation) {
    if (this.options.isAnalyticsEnabled()) {
      metricsProcessor.registerEvaluation(target, featureConfig.getFeature(), variation);
    }
  }

  public void close() {
    log.info("Closing the client");
    closing = true;
    off();
    authService.close();
    repository.close();
    pollProcessor.close();
    updateProcessor.close();
    metricsProcessor.close();
    connector.close();
    log.info("All resources released and client closed");
  }

  /* Package private */

  BaseConfig getOptions() {
    return options;
  }

  PollingProcessor getPollProcessor() {
    return pollProcessor;
  }
}
