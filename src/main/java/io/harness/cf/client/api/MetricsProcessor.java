package io.harness.cf.client.api;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricsProcessor extends AbstractScheduledService {

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

  private final Connector connector;
  private final MetricsCallback callback;
  private final BaseConfig config;
  private final BlockingQueue<MetricEvent> queue;

  private String jarVersion = "";

  public MetricsProcessor(
      @NonNull Connector connector, @NonNull BaseConfig config, @NonNull MetricsCallback callback) {
    this.connector = connector;
    this.config = config;
    this.callback = callback;
    this.queue = new LinkedBlockingQueue<>(config.getBufferSize());
    this.callback.onMetricsReady();
  }

  // push the incoming data to the ring buffer
  public void pushToQueue(Target target, FeatureConfig featureConfig, Variation variation) {

    if (queue.remainingCapacity() == 0) {
      executor().submit(this::runOneIteration);
    }

    try {
      queue.put(new MetricEvent(featureConfig, target, variation));
    } catch (InterruptedException e) {
      log.debug("Long waiting");
    }
  }

  /** This method sends the metrics data to the analytics server and resets the cache */
  public void sendDataAndResetCache(final List<MetricEvent> data) {
    log.debug("Reading from queue and building cache");
    jarVersion = getVersion();

    if (!data.isEmpty()) {

      Map<MetricEvent, Integer> map = new HashMap<>();
      for (MetricEvent event : data) {
        map.put(event, map.getOrDefault(event, 0) + 1);
      }

      // We will only submit summary metrics to the event server
      Metrics metrics = prepareSummaryMetricsBody(map);
      if (!metrics.getMetricsData().isEmpty() || !metrics.getTargetData().isEmpty()) {
        try {
          long startTime = System.currentTimeMillis();
          connector.postMetrics(metrics);
          long endTime = System.currentTimeMillis();
          if ((endTime - startTime) > config.getMetricsServiceAcceptableDuration()) {
            log.warn("Metrics service API duration=[{}]", (endTime - startTime));
          }
          log.info("Successfully sent analytics data to the server");
        } catch (ConnectorException e) {
          log.error("Exception while posting metrics to the event server");
        }
      }
      globalTargetSet.addAll(stagingTargetSet);
      stagingTargetSet.clear();
    }
  }

  protected Metrics prepareSummaryMetricsBody(Map<MetricEvent, Integer> data) {
    Metrics metrics = new Metrics();
    Map<SummaryMetrics, Integer> summaryMetricsData = new HashMap<>();
    addTargetData(
        metrics, Target.builder().name(GLOBAL_TARGET_NAME).identifier(GLOBAL_TARGET).build());
    for (Map.Entry<MetricEvent, Integer> entry : data.entrySet()) {
      Target target = entry.getKey().getTarget();
      addTargetData(metrics, target);
      SummaryMetrics summaryMetrics = prepareSummaryMetricsKey(entry.getKey());
      summaryMetricsData.put(summaryMetrics, entry.getValue());
    }

    for (Map.Entry<SummaryMetrics, Integer> entry : summaryMetricsData.entrySet()) {
      MetricsData metricsData = new MetricsData();
      metricsData.setTimestamp(System.currentTimeMillis());
      metricsData.count(entry.getValue());
      metricsData.setMetricsType(MetricsData.MetricsTypeEnum.FFMETRICS);
      metricsData.addAttributesItem(
          new KeyValue(FEATURE_NAME_ATTRIBUTE, entry.getKey().getFeatureName()));
      metricsData.addAttributesItem(
          new KeyValue(VARIATION_IDENTIFIER_ATTRIBUTE, entry.getKey().getVariationIdentifier()));
      metricsData.addAttributesItem(new KeyValue(TARGET_ATTRIBUTE, GLOBAL_TARGET));
      metricsData.addAttributesItem(new KeyValue(SDK_TYPE, SERVER));
      metricsData.addAttributesItem(new KeyValue(SDK_LANGUAGE, "java"));
      metricsData.addAttributesItem(new KeyValue(SDK_VERSION, jarVersion));
      metrics.addMetricsDataItem(metricsData);
    }
    return metrics;
  }

  private SummaryMetrics prepareSummaryMetricsKey(MetricEvent key) {
    return SummaryMetrics.builder()
        .featureName(key.getFeatureConfig().getFeature())
        .variationIdentifier(key.getVariation().getIdentifier())
        .variationValue(key.getVariation().getValue())
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
      attributes.forEach(
          (k, v) -> {
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
            targetData.addAttributesItem(keyValue);
          });

      targetData.setIdentifier(target.getIdentifier());
      if (Strings.isNullOrEmpty(target.getName())) {
        targetData.setName(target.getIdentifier());
      } else {
        targetData.setName(target.getName());
      }
      metrics.addTargetDataItem(targetData);
    }
  }

  private String getVersion() {
    return io.harness.cf.Version.VERSION;
  }

  @Override
  protected void runOneIteration() {
    List<MetricEvent> data = new ArrayList<>();
    queue.drainTo(data);
    sendDataAndResetCache(data);
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
        config.getFrequency(), config.getFrequency(), TimeUnit.SECONDS);
  }

  public void start() {
    log.info("Starting MetricsProcessor with request interval: {}", config.getFrequency());
    startAsync();
  }

  public void stop() {
    log.info("Stopping MetricsProcessor");
    stopAsync();
  }

  public void close() {
    stop();
    log.info("Closing MetricsProcessor");
  }
}
