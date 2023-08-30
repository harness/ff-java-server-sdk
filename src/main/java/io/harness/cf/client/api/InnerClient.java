package io.harness.cf.client.api;

import static com.google.common.util.concurrent.Service.State.*;

import com.google.gson.JsonObject;
import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.connector.*;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class InnerClient
    implements AutoCloseable,
        FlagEvaluateCallback,
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
    repository = new StorageRepository(options.getCache(), options.getStore(), this);
    evaluator = new Evaluator(repository);
    pollProcessor =
        new PollingProcessor(this.connector, repository, options.getPollIntervalInSeconds(), this);
    metricsProcessor = new MetricsProcessor(this.connector, this.options, this);
    updateProcessor = new UpdateProcessor(this.connector, this.repository, this);

    // start with authentication
    authenticateAsync();
  }

  private void authenticateAsync() {
    CompletableFuture.supplyAsync(this::authenticate);
  }

  private boolean authenticate() {

    final int AUTH_TIMEOUT_MS = 60_000;
    final long expireTime = System.currentTimeMillis() + AUTH_TIMEOUT_MS;
    final int delayMs = ThreadLocalRandom.current().nextInt(5000, 10000);
    boolean authenticated = false;
    int attempt = 1;

    do {
      try {
        connector.authenticate();
        SdkCodes.infoSdkAuthOk();
        onAuthSuccess();
        authenticated = true;
      } catch (ConnectorException e) {
        int currentDelayMs = delayMs * attempt++;

        if (!e.shouldRetry()) {
          log.error("Exception while authenticating", e);
          onFailure(e.getMessage());
          break;
        }

        log.warn(
            "Authentication attempt #{} failed, will retry in {}ms, error: {}",
            attempt,
            currentDelayMs,
            e.getMessage());
        try {
          Thread.sleep(currentDelayMs);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
      }
    } while (!authenticated && System.currentTimeMillis() < expireTime);

    if (System.currentTimeMillis() >= expireTime) {
      log.warn("auth timeout: Was not able to authenticate within {}ms", AUTH_TIMEOUT_MS);
    }

    return authenticated;
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

    authenticateAsync();

    log.info("Finished re-auth");
  }

  private void onAuthSuccess() {
    log.info("SDK successfully logged in");
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
      log.info("onDisconnected triggered, starting poller to get latest flags");

      pollProcessor.close();
      pollProcessor =
          new PollingProcessor(connector, repository, options.getPollIntervalInSeconds(), this);
      pollProcessor.start();
    } else {
      log.info(
          "Poller already running [closing={} interval={}]",
          closing,
          options.getPollIntervalInSeconds());
      log.info("SSE disconnect detected - asking poller to refresh flags");
      pollProcessor.retrieveAll();
    }
  }

  @Override
  public void onReady() {
    log.info("onReady triggered");
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
    log.info("update triggered [event={} manual={}] ", message.getEvent(), manual);
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
        log.info("PollingProcessor ready");
        break;
      case STREAM:
        streamReady = true;
        log.info("Updater ready");
        break;
      case METRICS:
        metricReady = true;
        log.info("MetricsProcessor ready");
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
