package io.harness.cf.client.api;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.AbstractScheduledService;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MetricsProcessor extends AbstractScheduledService {

  private static final String FEATURE_NAME_ATTRIBUTE = "featureName";
  private static final String VARIATION_IDENTIFIER_ATTRIBUTE = "variationIdentifier";
  private static final Set<Target> globalTargetSet = new HashSet<>();
  private static final Set<Target> stagingTargetSet = new HashSet<>();
  private static final String SDK_TYPE = "SDK_TYPE";

  private static final String SERVER = "server";
  private static final String SDK_LANGUAGE = "SDK_LANGUAGE";
  private static final String SDK_VERSION = "SDK_VERSION";

  private final Connector connector;
  private final BaseConfig config;
  private final BlockingQueue<MetricEvent> queue;
  private final List<MetricEvent> metricEvents = Collections.synchronizedList(new ArrayList<>());

  private volatile boolean closing = false;

  private String jarVersion = "";

  ExecutorService executorService = Executors.newFixedThreadPool(10);

  public MetricsProcessor(
      @NonNull Connector connector, @NonNull BaseConfig config, @NonNull MetricsCallback callback) {
    this.connector = connector;
    this.config = config;
    this.queue = new LinkedBlockingQueue<>(config.getBufferSize());
    callback.onMetricsReady();
  }

  // push the incoming data to the queue
  public void pushToQueue(Target target, FeatureConfig featureConfig, Variation variation) {

    try {
      queue.put(new MetricEvent(featureConfig, target, variation));
    } catch (InterruptedException e) {
      log.debug("Queue is blocked for a long time");
      Thread.currentThread().interrupt();
    }
  }

  protected void consumer() {
    log.info("Metrics Consumer started");
    while (true) {
      if (closing) {
        break;
      }
      try {
        MetricEvent metricEvent = queue.take();
        log.info("Metrics event {}", metricEvent.getTarget().getIdentifier());
        metricEvents.add(metricEvent);
      } catch (InterruptedException e) {
        log.error("Metrics Consumer raised exception", e);
        Thread.currentThread().interrupt();
      }
    }
    log.info("Metrics Consumer ended");
  }

  /** This method sends the metrics data to the analytics server and resets the cache */
  public void sendDataAndResetCache(final List<MetricEvent> data) {
    log.info("Reading from queue and preparing the metrics");
    jarVersion = getVersion();

    if (!data.isEmpty()) {

      Map<MetricEvent, Integer> map = new HashMap<>();
      for (MetricEvent event : data) {
        map.put(event, map.getOrDefault(event, 0) + 1);
      }

      log.info("Preparing summary metrics");
      // We will only submit summary metrics to the event server
      Metrics metrics = prepareSummaryMetricsBody(map);
      if ((metrics.getMetricsData() != null && !metrics.getMetricsData().isEmpty())
          || (metrics.getTargetData() != null && !metrics.getTargetData().isEmpty())) {
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
    metrics.metricsData(new ArrayList<>());
    metrics.targetData(new ArrayList<>());

    Map<SummaryMetrics, Integer> summaryMetricsData = new HashMap<>();
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
      metricsData.attributes(
          Arrays.asList(
              new KeyValue(FEATURE_NAME_ATTRIBUTE, entry.getKey().getFeatureName()),
              new KeyValue(VARIATION_IDENTIFIER_ATTRIBUTE, entry.getKey().getVariationIdentifier()),
              new KeyValue(SDK_TYPE, SERVER),
              new KeyValue(SDK_LANGUAGE, "java"),
              new KeyValue(SDK_VERSION, jarVersion)));
      if (metrics.getMetricsData() != null) {
        metrics.getMetricsData().add(metricsData);
      }
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
      if (Strings.isNullOrEmpty(target.getName())) {
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
    this.flush();
  }

  @NonNull
  @Override
  protected Scheduler scheduler() {
    return Scheduler.newFixedDelaySchedule(
        config.getFrequency(), config.getFrequency(), TimeUnit.SECONDS);
  }

  public void flush() {
    // maybe lock?
    List<MetricEvent> data = new ArrayList<>(this.metricEvents);
    this.metricEvents.clear();
    try {
      //          sendDataAndResetCache(data);
    } catch (Exception e) {
      log.error("Error while executing runOneIteration", e);
    }
  }

  public void start() {
    log.info("Starting MetricsProcessor with request interval: {}", config.getFrequency());
    startAsync();
    for (int i = 0; i < 10; i++) {
      executorService.submit(this::consumer);
    }
  }

  public void stop() {
    log.info("Stopping MetricsProcessor");
    stopAsync();
    executorService.shutdown();
  }

  public void close() {
    this.flush();
    stop();
    closing = true;
    log.info("Closing MetricsProcessor");
  }
}
