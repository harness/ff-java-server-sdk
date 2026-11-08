package io.harness.cf.client.api;

import static io.harness.cf.client.common.SdkCodes.warnMetricsBufferFull;
import static io.harness.cf.client.common.Utils.shutdownExecutorService;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gson.Gson;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    void add(K key, long count) {
      freqMap.merge(key, count, Long::sum);
    }

    void clear() {
      freqMap.clear();
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
  private final Set<String> globalTargetSet = new LinkedHashSet<>();
  private final Object stateLock = new Object();
  private final Object flushLock = new Object();
  private final AtomicBoolean closing = new AtomicBoolean();
  private volatile boolean closed;
  private final Gson gson = new Gson();
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

  private final LongAdder evalCounter = new LongAdder();
  private final LongAdder metricsEvalsDropped = new LongAdder();
  private final LongAdder targetsSeenDropped = new LongAdder();
  private final Connector connector;
  private final BaseConfig config;
  private final FrequencyMap<MetricEvent> frequencyMap;
  private final Set<Target> targetsSeen;

  private ScheduledFuture<?> runningTask = null;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  private final LongAdder metricsSent = new LongAdder();
  private final int maxFreqMapSize;

  private final boolean shouldFlushMetricsOnClose;

  public MetricsProcessor(
      @NonNull Connector connector,
      @NonNull BaseConfig config,
      @NonNull MetricsCallback callback,
      boolean shouldFlushMetricsOnClose) {
    this.connector = connector;
    this.config = config;
    this.frequencyMap = new FrequencyMap<>();
    this.targetsSeen = ConcurrentHashMap.newKeySet();
    this.maxFreqMapSize = clamp(config.getBufferSize(), 2048, MAX_FREQ_MAP_TO_RETAIN);
    this.shouldFlushMetricsOnClose = shouldFlushMetricsOnClose;
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
    synchronized (stateLock) {
      if (closing.get()) return;

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
  }

  /** This method sends the metrics data to the analytics server and resets the cache */
  public void sendDataAndResetCache(
      final Map<MetricEvent, Long> freqMap, final Set<Target> uniqueTargets) {
    synchronized (flushLock) {
      if (closed || (freqMap.isEmpty() && uniqueTargets.isEmpty())) return;
      Metrics metrics = prepareSummaryMetricsBody(freqMap, uniqueTargets);
      if (metrics.getMetricsData().isEmpty() && metrics.getTargetData().isEmpty()) return;
      try {
        long startTime = System.currentTimeMillis();
        connector.postMetrics(metrics);
        metricsSent.add(sumOfValuesInMap(freqMap));
        synchronized (stateLock) {
          if (!closed) {
            for (TargetData target : metrics.getTargetData()) {
              globalTargetSet.add(fingerprint(target));
            }
            Iterator<String> oldest = globalTargetSet.iterator();
            while (globalTargetSet.size() > MAX_SENT_TARGETS_TO_RETAIN) {
              oldest.next();
              oldest.remove();
            }
          }
        }
        long duration = System.currentTimeMillis() - startTime;
        if (duration > config.getMetricsServiceAcceptableDuration()) {
          log.warn("Metrics service API duration=[{}]", duration);
        }
      } catch (ConnectorException ex) {
        SdkCodes.warnPostMetricsFailed(ex.getMessage());
        retainFailedBatch(freqMap, uniqueTargets);
      }
    }
  }

  private void retainFailedBatch(Map<MetricEvent, Long> events, Set<Target> targets) {
    synchronized (stateLock) {
      if (closing.get()) return;
      events.forEach(
          (event, count) -> {
            if (frequencyMap.containsKey(event) || frequencyMap.size() < maxFreqMapSize) {
              frequencyMap.add(event, count);
            } else {
              metricsEvalsDropped.add(count);
            }
          });
      for (Target target : targets) {
        if (targetsSeen.contains(target) || targetsSeen.size() < MAX_SENT_TARGETS_TO_RETAIN) {
          targetsSeen.add(target);
        } else {
          targetsSeenDropped.increment();
        }
      }
    }
  }

  private String fingerprint(TargetData target) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(gson.toJson(target).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required by the JVM", ex);
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
    if (Boolean.TRUE.equals(target.isPrivate())) return;
    Set<String> privateAttributes = target.getPrivateAttributes();
    TargetData data = new TargetData();
    data.setIdentifier(target.getIdentifier());
    data.setName(
        StringUtils.isNullOrEmpty(target.getName()) ? target.getIdentifier() : target.getName());
    for (Map.Entry<String, Object> entry : target.getAttributes().entrySet()) {
      if (!privateAttributes.contains(entry.getKey())) {
        data.getAttributes().add(new KeyValue(entry.getKey(), String.valueOf(entry.getValue())));
      }
    }
    synchronized (stateLock) {
      if (!globalTargetSet.contains(fingerprint(data))) metrics.getTargetData().add(data);
    }
  }

  void runOneIteration() {
    Thread.currentThread().setName("MetricsThread");
    synchronized (flushLock) {
      final Map<MetricEvent, Long> events;
      final Set<Target> targets;
      synchronized (stateLock) {
        if (closed) return;
        long droppedEvents = metricsEvalsDropped.sumThenReset();
        long droppedTargets = targetsSeenDropped.sumThenReset();
        if (droppedEvents > 0 || droppedTargets > 0) {
          warnMetricsBufferFull(droppedEvents, droppedTargets);
        }
        events = frequencyMap.drainToMap();
        targets = new HashSet<>(targetsSeen);
        targetsSeen.clear();
      }
      sendDataAndResetCache(events, targets);
    }
  }

  public synchronized void start() {
    if (closing.get() || isRunning()) {
      return;
    }
    runningTask =
        scheduler.scheduleAtFixedRate(
            this::runOneIteration, config.getFrequency() / 2, config.getFrequency(), SECONDS);
    SdkCodes.infoMetricsThreadStarted(config.getFrequency());
  }

  public synchronized void stop() {
    if (scheduler.isShutdown()) return;
    if (shouldFlushMetricsOnClose && config.isAnalyticsEnabled()) {
      flushQueue();
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
    if (!closing.compareAndSet(false, true)) return;
    try {
      synchronized (this) {
        stop();
        scheduler.shutdown();
      }
      shutdownExecutorService(
          scheduler,
          SdkCodes::infoMetricsThreadExited,
          message -> log.warn("Failed to stop metrics scheduler: {}", message));
    } finally {
      synchronized (stateLock) {
        closed = true;
        frequencyMap.clear();
        targetsSeen.clear();
        globalTargetSet.clear();
      }
    }
  }

  public synchronized boolean isRunning() {
    return runningTask != null && !runningTask.isCancelled();
  }

  /* package private */

  synchronized void flushQueue() {
    if (!scheduler.isShutdown()) scheduler.schedule(this::runOneIteration, 0, SECONDS);
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
    synchronized (stateLock) {
      globalTargetSet.clear();
    }
  }
}
