package io.harness.cf.client.api;

import com.google.common.util.concurrent.AbstractScheduledService;
import com.google.common.util.concurrent.AtomicLongMap;
import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.common.StringUtils;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricsProcessor extends AbstractScheduledService {

  static class FrequencyMap<K> {

    private final AtomicLongMap<K> map;

    FrequencyMap() {
      map = AtomicLongMap.create();
    }

    void increment(K key) {
      map.incrementAndGet(key);
    }

    long get(K key) {
      return map.get(key);
    }

    int size() {
      return map.size();
    }

    long sum() {
      return map.sum();
    }

    Map<K, Long> drainToMap() {
      // Guava doesn't have a function to atomically drain an AtomicLongMap.
      // Here we need to atomically set each key to zero as we transfer it to the new map else we
      // see missed evaluations
      final HashMap<K, Long> result = new HashMap<>();
      map.asMap()
          .forEach(
              (k, v) -> {
                final long oldVal = map.getAndUpdate(k, old -> 0);
                result.put(k, oldVal);
              });
      map.removeAllZeros();
      return result;
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

  private final Connector connector;
  private final BaseConfig config;
  private final FrequencyMap<MetricEvent> frequencyMap;
  private final Set<Target> uniqueTargetSet;
  private final ScheduledExecutorService executor;

  private String jarVersion = "";

  private final LongAdder metricsSent = new LongAdder();

  public MetricsProcessor(
      @NonNull Connector connector, @NonNull BaseConfig config, @NonNull MetricsCallback callback) {
    this.connector = connector;
    this.config = config;
    this.executor = executor();

    this.frequencyMap = new FrequencyMap<>();
    this.uniqueTargetSet = ConcurrentHashMap.newKeySet();

    callback.onMetricsReady();
  }

  @Deprecated /* The name of this method no longer makes sense since we moved to a map, kept for source compatibility */
  public void pushToQueue(Target target, String featureName, Variation variation) {
    registerEvaluation(target, featureName, variation);
  }

  void registerEvaluation(Target target, String featureName, Variation variation) {

    final int freqMapSize = frequencyMap.size();

    if (freqMapSize > config.getBufferSize()) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Metric frequency map exceeded buffer size ({} > {}), force flushing",
            freqMapSize,
            config.getBufferSize());
      }
      // If the map is starting to grow too much then push the events now and reset the counters
      executor.submit(this::runOneIteration);
    }

    Target metricTarget = globalTarget;

    if (target != null) {
      uniqueTargetSet.add(target);
      if (!config.isGlobalTargetEnabled()) {
        metricTarget = target;
      }
    }

    frequencyMap.increment(new MetricEvent(featureName, metricTarget, variation));
  }

  /** This method sends the metrics data to the analytics server and resets the cache */
  public void sendDataAndResetCache(
      final Map<MetricEvent, Long> freqMap, final Set<Target> uniqueTargets) {
    log.info("Reading from queue and preparing the metrics");
    jarVersion = getVersion();

    if (!freqMap.isEmpty()) {

      log.info("Preparing summary metrics");
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
          log.info("Successfully sent analytics data to the server");
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
                  new KeyValue(SDK_VERSION, jarVersion)));
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

  private String getVersion() {
    return io.harness.cf.Version.VERSION;
  }

  @Override
  protected void runOneIteration() {
    if (log.isDebugEnabled()) {
      log.debug(
          "Drain metrics queue : frequencyMap size={} uniqueTargetSet size={}",
          frequencyMap.size(),
          uniqueTargetSet.size());
    }
    sendDataAndResetCache(frequencyMap.drainToMap(), new HashSet<>(uniqueTargetSet));

    uniqueTargetSet.clear();
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
        config.getFrequency(), config.getFrequency(), TimeUnit.SECONDS);
  }

  public void start() {
    SdkCodes.infoMetricsThreadStarted(config.getFrequency());
    startAsync();
  }

  public void stop() {
    log.debug("Stopping MetricsProcessor");
    stopAsync();
    SdkCodes.infoMetricsThreadExited();
  }

  public void close() {
    stop();
    log.info("Closing MetricsProcessor");
  }

  /* package private */

  synchronized void flushQueue() {
    executor.submit(this::runOneIteration);
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
    return uniqueTargetSet.size();
  }

  void reset() {
    stagingTargetSet.clear();
    globalTargetSet.clear();
  }
}
