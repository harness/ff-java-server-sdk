package io.harness.cf.client.api.analytics;

import com.google.common.base.Strings;
import io.harness.cf.ApiException;
import io.harness.cf.api.MetricsApi;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Analytics;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import io.jsonwebtoken.lang.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * This class prepares the message body for metrics and posts it to the server
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
@Slf4j
public class AnalyticsPublisherService {

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

  private String jarVerion = "";

  private final MetricsApi metricsAPI;
  private final Cache analyticsCache;
  private final String environmentID;
  private final String cluster;
  private final Config config;

  public AnalyticsPublisherService(
      MetricsApi metricsAPI,
      Config config,
      String environmentID,
      String cluster,
      Cache analyticsCache) {

    this.metricsAPI = metricsAPI;
    this.analyticsCache = analyticsCache;
    this.environmentID = environmentID;
    this.cluster = cluster;
    this.config = config;
  }

  /** This method sends the metrics data to the analytics server and resets the cache */
  public void sendDataAndResetCache() throws CfClientException {
    log.debug("Reading from queue and building cache");
    jarVerion = getVersion();

    final Map<Analytics, Integer> all = analyticsCache.getAll();
    if (!all.isEmpty()) {
      try {
        // We will only submit summary metrics to the event server
        Metrics metrics = prepareSummaryMetricsBody(all);
        if (!Collections.isEmpty(metrics.getMetricsData())
            || !Collections.isEmpty(metrics.getTargetData())) {
          long startTime = System.currentTimeMillis();
          metricsAPI.postMetrics(environmentID, cluster, metrics);
          long endTime = System.currentTimeMillis();
          if ((endTime - startTime) > config.getMetricsServiceAcceptableDuration()) {
            log.warn("Metrics service API duration=[{}]", (endTime - startTime));
          }
        }
        globalTargetSet.addAll(stagingTargetSet);
        stagingTargetSet.clear();
        log.debug("Successfully sent analytics data to the server");
        analyticsCache.resetCache();
      } catch (ApiException e) {
        // Clear the set because the cache is only invalidated when there is no
        // exception, so the targets will reappear in the next iteration
        log.error("Failed to send metricsData {} : {}", e.getMessage(), e.getCode());
      }
    }
  }

  protected Metrics prepareSummaryMetricsBody(Map<Analytics, Integer> data) {
    Metrics metrics = new Metrics();
    Map<SummaryMetrics, Integer> summaryMetricsData = new HashMap<>();
    addTargetData(
        metrics, Target.builder().name(GLOBAL_TARGET_NAME).identifier(GLOBAL_TARGET).build());
    for (Map.Entry<Analytics, Integer> entry : data.entrySet()) {
      Target target = entry.getKey().getTarget();
      addTargetData(metrics, target);
      SummaryMetrics summaryMetrics = prepareSummaryMetricsKey(entry.getKey());
      final Integer summaryCount = summaryMetricsData.get(summaryMetrics);
      Integer total = entry.getValue();
      if (summaryCount != null) {
        total = summaryCount + entry.getValue();
      }
      summaryMetricsData.put(summaryMetrics, total);
    }

    for (Map.Entry<SummaryMetrics, Integer> entry : summaryMetricsData.entrySet()) {
      MetricsData metricsData = new MetricsData();
      metricsData.setTimestamp(System.currentTimeMillis());
      metricsData.count(entry.getValue());
      metricsData.setMetricsType(MetricsData.MetricsTypeEnum.FFMETRICS);
      setMetricsAttributes(metricsData, FEATURE_NAME_ATTRIBUTE, entry.getKey().getFeatureName());
      setMetricsAttributes(
          metricsData, VARIATION_IDENTIFIER_ATTRIBUTE, entry.getKey().getVariationIdentifier());
      setMetricsAttributes(metricsData, TARGET_ATTRIBUTE, GLOBAL_TARGET);
      setMetricsAttributes(metricsData, SDK_TYPE, SERVER);

      setMetricsAttributes(metricsData, SDK_LANGUAGE, "java");
      setMetricsAttributes(metricsData, SDK_VERSION, jarVerion);
      metrics.addMetricsDataItem(metricsData);
    }
    return metrics;
  }

  private SummaryMetrics prepareSummaryMetricsKey(Analytics key) {
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
            if ((!Collections.isEmpty(privateAttributes))) {
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

  private void setMetricsAttributes(MetricsData metricsData, String key, String value) {
    KeyValue metricsAttributes = new KeyValue();
    metricsAttributes.setKey(key);
    metricsAttributes.setValue(value);
    metricsData.addAttributesItem(metricsAttributes);
  }

  private String getVersion() throws CfClientException {
    try {
      return io.harness.cf.Version.VERSION;
    } catch (Exception e) {
      throw new CfClientException("Exception happened while getting the version " + e.getMessage());
    }
  }
}
