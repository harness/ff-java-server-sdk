package io.harness.cf.client.api;

import static io.harness.cf.client.common.SdkCodes.warnMetricsBufferFull;
import static io.harness.cf.client.common.Utils.shutdownExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.harness.cf.Version;
import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.common.StringUtils;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.KeyValue;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.MetricsData;
import io.harness.cf.model.TargetData;
import io.harness.cf.model.Variation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricsProcessor {

  static class FrequencyMap<K> {

    private final ConcurrentHashMap<K, Long> freqMap;

    FrequencyMap() {
      freqMap = new ConcurrentHashMap<>();
    }

    void increment(K key) {
      freqMap.compute(key, (k, v) -> (v == null) ? 1L : v + 1L);
    }

    int size() {
      return freqMap.size();
    }

    long sum() {
      return freqMap.values().stream().mapToLong(Long::longValue).sum();
    }

    Map<K, Long> drainToMap() {
      // ConcurrentHashMap doesn't have a function to atomically drain an AtomicLongMap.
      // Here we need to atomically set each key to zero as we transfer it to the new map else we
      // see missed evaluations
      final HashMap<K, Long> snapshotMap = new HashMap<>();
      freqMap.forEach((k, v) -> transferValueIntoMapAtomicallyAndUpdateTo(k, snapshotMap, 0));
      snapshotMap.forEach((k, v) -> freqMap.remove(k, 0L));

      if (log.isTraceEnabled()) {
        log.trace(
            "snapshot got {} events",
            snapshotMap.values().stream().mapToLong(Long::longValue).sum());
      }
      return snapshotMap;
    }

    private void transferValueIntoMapAtomicallyAndUpdateTo(
        K key, Map<K, Long> targetMap, long newValue) {
      freqMap.computeIfPresent(
          key,
          (k, v) -> {
            targetMap.put(k, v);
            return newValue;
          });
    }

    public boolean containsKey(K key) {
      return freqMap.containsKey(key);
    }
  }

  private static final String FEATURE_NAME_ATTRIBUTE = "featureName";
  private static final String VARIATION_IDENTIFIER_ATTRIBUTE = "variationIdentifier";
  private static final String TARGET_ATTRIBUTE = "target";
  private static final Set<Target> globalTargetSet = new HashSet<>();
  private static final Set<Target> stagingTargetSet = new HashSet<>();
  private static final String SDK_TYPE = "SDK_TYPE";

  /** This target identifier is used to aggregate and send data for all targets as a summary */
  private static final String GLOBAL_TARGET = "__global__cf_target";

  private static final String GLOBAL_TARGET_NAME = "Global Target";

  private static final String SERVER = "server";
  private static final String SDK_LANGUAGE = "SDK_LANGUAGE";
  private static final String SDK_VERSION = "SDK_VERSION";

  private static final Target globalTarget =
      Target.builder().name(GLOBAL_TARGET_NAME).identifier(GLOBAL_TARGET).build();

  private static final int MAX_SENT_TARGETS_TO_RETAIN = 100_000;
  private static final int MAX_FREQ_MAP_TO_RETAIN = 10_000;

  private static final LongAdder evalCounter = new LongAdder();
  private static final LongAdder metricsEvalsDropped = new LongAdder();
  private static final LongAdder targetsSeenDropped = new LongAdder();
  private final Connector connector;
  private final BaseConfig config;
  private final FrequencyMap<MetricEvent> frequencyMap;
  private final Set<Target> targetsSeen;

  private ScheduledFuture<?> runningTask = null;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private final LongAdder metricsSent = new LongAdder();
  private final int maxFreqMapSize;

  public MetricsProcessor(
      @NonNull Connector connector, @NonNull BaseConfig config, @NonNull MetricsCallback callback) {
    this.connector = connector;
    this.config = config;
    this.frequencyMap = new FrequencyMap<>();
    this.targetsSeen = ConcurrentHashMap.newKeySet();
    this.maxFreqMapSize = clamp(config.getBufferSize(), 2048, MAX_FREQ_MAP_TO_RETAIN);
    callback.onMetricsReady();
  }

  private int clamp(int value, int lower, int higher) {
    return Math.max(lower, Math.min(higher, value));
  }

  @Deprecated /* The name of this method no longer makes sense since we moved to a map, kept for source compatibility */
  public void pushToQueue(Target target, String featureName, Variation variation) {
    registerEvaluation(target, featureName, variation);
  }

  void registerEvaluation(Target target, String featureName, Variation variation) {

    Target metricTarget = globalTarget;

    if (target != null) {
      if (!targetsSeen.contains(target) && targetsSeen.size() + 1 > MAX_SENT_TARGETS_TO_RETAIN) {
        targetsSeenDropped.increment();
      } else {
        targetsSeen.add(target);
        if (!config.isGlobalTargetEnabled()) {
          metricTarget = target;
        }
      }
    }

    final MetricEvent metricsEvent = new MetricEvent(featureName, metricTarget, variation);

    if (!frequencyMap.containsKey(metricsEvent) && frequencyMap.size() + 1 > maxFreqMapSize) {
      metricsEvalsDropped.increment();
    } else {
      frequencyMap.increment(metricsEvent);
    }

    evalCounter.increment();
  }

  /** This method sends the metrics data to the analytics server and resets the cache */
  public void sendDataAndResetCache(
      final Map<MetricEvent, Long> freqMap, final Set<Target> uniqueTargets) {

    log.debug("Reading from queue and preparing the metrics");

    if (!freqMap.isEmpty()) {

      log.debug("Preparing summary metrics");
      // We will only submit summary metrics to the event server
      Metrics metrics = prepareSummaryMetricsBody(freqMap, uniqueTargets);
      if ((metrics.getMetricsData() != null && !metrics.getMetricsData().isEmpty())
          || (metrics.getTargetData() != null && !metrics.getTargetData().isEmpty())) {
        try {
          long startTime = System.currentTimeMillis();
          connector.postMetrics(metrics);

          metricsSent.add(sumOfValuesInMap(freqMap));
          long endTime = System.currentTimeMillis();
          if ((endTime - startTime) > config.getMetricsServiceAcceptableDuration()) {
            log.warn("Metrics service API duration=[{}]", (endTime - startTime));
          }
          log.debug("Successfully sent analytics data to the server");
        } catch (ConnectorException e) {
          SdkCodes.warnPostMetricsFailed(e.getMessage());
        }
      }
      globalTargetSet.addAll(stagingTargetSet);
      stagingTargetSet.clear();
    }
  }

  private long sumOfValuesInMap(Map<?, Long> map) {
    return map.entrySet().stream().mapToLong(Map.Entry::getValue).sum();
  }

  protected Metrics prepareSummaryMetricsBody(Map<MetricEvent, Long> data, Set<Target> targets) {
    final Metrics metrics = new Metrics(new ArrayList<>(), new ArrayList<>());
    final Map<SummaryMetrics, Long> summaryMetricsData = new HashMap<>();

    targets.forEach(target -> addTargetData(metrics, target));

    data.forEach(
        (target, count) ->
            summaryMetricsData.put(
                prepareSummaryMetricsKey(target, target.getTarget().getIdentifier()), count));

    summaryMetricsData.forEach(
        (summary, count) -> {
          MetricsData metricsData = new MetricsData();
          metricsData.setTimestamp(System.currentTimeMillis());
          metricsData.count(count.intValue());
          metricsData.setMetricsType(MetricsData.MetricsTypeEnum.FFMETRICS);
          metricsData.attributes(
              Arrays.asList(
                  new KeyValue(FEATURE_NAME_ATTRIBUTE, summary.getFeatureName()),
                  new KeyValue(VARIATION_IDENTIFIER_ATTRIBUTE, summary.getVariationIdentifier()),
                  new KeyValue(TARGET_ATTRIBUTE, summary.getTargetIdentifier()),
                  new KeyValue(SDK_TYPE, SERVER),
                  new KeyValue(SDK_LANGUAGE, "java"),
                  new KeyValue(SDK_VERSION, Version.VERSION)));
          if (metrics.getMetricsData() != null) {
            metrics.getMetricsData().add(metricsData);
          }
        });
    return metrics;
  }

  private SummaryMetrics prepareSummaryMetricsKey(MetricEvent key, String targetIdentifier) {
    return SummaryMetrics.builder()
        .featureName(key.getFeatureName())
        .variationIdentifier(key.getVariation().getIdentifier())
        .variationValue(key.getVariation().getValue())
        .targetIdentifier(targetIdentifier)
        .build();
  }

  private void addTargetData(Metrics metrics, Target target) {
    Set<String> privateAttributes = target.getPrivateAttributes();
    TargetData targetData = new TargetData();

    if (!stagingTargetSet.contains(target)
        && !globalTargetSet.contains(target)
        && !target.isPrivate()) {

      stagingTargetSet.add(target);
      final Map<String, Object> attributes = target.getAttributes();
      for (Map.Entry<String, Object> entry : attributes.entrySet()) {
        String k = entry.getKey();
        Object v = entry.getValue();
        KeyValue keyValue = new KeyValue();
        if ((!privateAttributes.isEmpty())) {
          if (!privateAttributes.contains(k)) {
            keyValue.setKey(k);
            keyValue.setValue(v.toString());
          }
        } else {
          keyValue.setKey(k);
          keyValue.setValue(v.toString());
        }
        targetData.getAttributes().add(keyValue);
      }

      targetData.setIdentifier(target.getIdentifier());
      if (StringUtils.isNullOrEmpty(target.getName())) {
        targetData.setName(target.getIdentifier());
      } else {
        targetData.setName(target.getName());
      }
      if (metrics.getTargetData() != null) {
        metrics.getTargetData().add(targetData);
      }
    }
  }

  void runOneIteration() {
    Thread.currentThread().setName("MetricsThread");

    final long droppedEvals = metricsEvalsDropped.sumThenReset();
    final long droppedTargets = targetsSeenDropped.sumThenReset();

    if (droppedEvals > 0 || droppedTargets > 0) {
      warnMetricsBufferFull(droppedEvals, droppedTargets);
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Drain metrics queue : frequencyMap size={} uniqueTargetSet size={}",
          frequencyMap.size(),
          targetsSeen.size());
    }
    sendDataAndResetCache(frequencyMap.drainToMap(), new HashSet<>(targetsSeen));

    targetsSeen.clear();
  }

  public void start() {
    if (isRunning()) {
      return;
    }
    runningTask =
        scheduler.scheduleAtFixedRate(
            this::runOneIteration, config.getFrequency() / 2, config.getFrequency(), SECONDS);
    SdkCodes.infoMetricsThreadStarted(config.getFrequency());
  }

  public void stop() {
    if (config.isFlushAnalyticsOnClose()) {
      flushWithTimeout(10);
    }

    log.debug("Stopping MetricsProcessor");
    if (scheduler.isShutdown()) {
      return;
    }

    if (runningTask == null) {
      return;
    }

    runningTask.cancel(false);
    runningTask = null;
  }

  public void close() {
    stop();

    shutdownExecutorService(
        scheduler,
        SdkCodes::infoMetricsThreadExited,
        errMsg -> log.warn("failed to stop metrics scheduler: {}", errMsg));

    log.debug("Closing MetricsProcessor");
  }

  public boolean isRunning() {
    return runningTask != null && !runningTask.isCancelled();
  }

  public synchronized void flushWithTimeout(int timeoutInSeconds) {
    log.debug("Flushing metrics with timeout: {} seconds", timeoutInSeconds);
    ScheduledFuture<?> future = null;
    try {
      future = flushQueue();

      // Wait for the task to complete or timeout
      future.get(timeoutInSeconds, SECONDS);
      log.debug("Metrics successfully flushed within the timeout of {} seconds", timeoutInSeconds);

    } catch (TimeoutException e) {
      log.debug(
          "Metrics flush did not complete within the timeout of {} seconds", timeoutInSeconds);
      // Forcefully cancel the flush if it times out
      future.cancel(true);

    } catch (InterruptedException | ExecutionException e) {
      log.error("Error occurred during metrics flush", e);
      Thread.currentThread().interrupt();
    }
  }

  /* package private */

  synchronized ScheduledFuture<?> flushQueue() {
    scheduler.schedule(this::runOneIteration, 0, SECONDS);
    return null;
  }

  long getMetricsSent() {
    return metricsSent.sum();
  }

  long getPendingMetricsToBeSent() {
    return frequencyMap.sum();
  }

  long getQueueSize() {
    return frequencyMap.size();
  }

  long getTargetSetSize() {
    return targetsSeen.size();
  }

  long getMetricsEvalsDropped() {
    return metricsEvalsDropped.sum();
  }

  long getTargetsSeenDropped() {
    return targetsSeenDropped.sum();
  }

  void reset() {
    stagingTargetSet.clear();
    globalTargetSet.clear();
  }
}
