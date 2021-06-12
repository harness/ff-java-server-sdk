package io.harness.cf.client.api.analytics;

import com.google.common.base.Strings;
import io.harness.cf.client.api.CfClientException;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.dto.Analytics;
import io.harness.cf.client.dto.Target;
import io.harness.cf.metrics.ApiException;
import io.harness.cf.metrics.api.DefaultApi;
import io.harness.cf.metrics.model.KeyValue;
import io.harness.cf.metrics.model.Metrics;
import io.harness.cf.metrics.model.MetricsData;
import io.harness.cf.metrics.model.TargetData;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import io.jsonwebtoken.lang.Collections;
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
  private static final String FEATURE_VALUE_ATTRIBUTE = "featureValue";
  private static final String VARIATION_VALUE_ATTRIBUTE = "featureValue";
  private static final String VARIATION_IDENTIFIER_ATTRIBUTE = "variationIdentifier";
  private static final String TARGET_ATTRIBUTE = "target";
  private static final Set<Target> globalTargetSet = new HashSet<>();
  private static final Set<Target> stagingTargetSet = new HashSet<>();
  private static final String JAR_VERSION = "JAR_VERSION";
  private static final String SDK_TYPE = "SDK_TYPE";
  private static final String ANONYMOUS_TARGET = "anonymous";
  private static final String SERVER = "server";
  private static final String SDK_LANGUAGE = "SDK_LANGUAGE";
  private static final String SDK_VERSION = "SDK_VERSION";

  private String jarVerion = "";

  private final DefaultApi metricsAPI;
  private final Cache analyticsCache;
  private final String environmentID;
  private final String cluster;
  private final Config config;

  public AnalyticsPublisherService(
      String apiKey, Config config, String environmentID, String cluster, Cache analyticsCache) {

    metricsAPI = MetricsApiFactory.create(apiKey, config);
    this.analyticsCache = analyticsCache;
    this.environmentID = environmentID;
    this.cluster = cluster;
    this.config = config;
  }

  /**
   * This method sends the metrics data to the analytics server and resets the cache
   *
   * @throws CfClientException
   */
  public void sendDataAndResetCache() throws CfClientException {
    log.debug("Reading from queue and building cache");
    jarVerion = getVersion();

    final Map<Analytics, Integer> all = analyticsCache.getAll();
    if (!all.isEmpty()) {
      try {
        Metrics metrics = prepareMessageBody(all);
        if (!Collections.isEmpty(metrics.getMetricsData())
            || !Collections.isEmpty(metrics.getTargetData())) {
          long startTime = System.currentTimeMillis();
          metricsAPI.postMetrics(environmentID, cluster, metrics);
          long endTime = System.currentTimeMillis();
          if ((endTime - startTime) > config.getMetricsServiceAcceptableDuration()) {
            log.warn("Metrics service API duratopm=[{}]", (endTime - startTime));
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

  private Metrics prepareMessageBody(Map<Analytics, Integer> all) {
    Metrics metrics = new Metrics();

    // using for-each loop for iteration over Map.entrySet()
    for (Map.Entry<Analytics, Integer> entry : all.entrySet()) {
      // Set target data
      TargetData targetData = new TargetData();
      // Set Metrics data
      MetricsData metricsData = new MetricsData();

      Analytics analytics = entry.getKey();
      final Set<String> privateAttributes = analytics.getTarget().getPrivateAttributes();
      final Target target = analytics.getTarget();
      final FeatureConfig featureConfig = analytics.getFeatureConfig();
      final Variation variation = analytics.getVariation();
      if (!globalTargetSet.contains(target) && !target.isPrivate()) {
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

      metricsData.setTimestamp(System.currentTimeMillis());
      metricsData.count(entry.getValue());
      metricsData.setMetricsType(MetricsData.MetricsTypeEnum.FFMETRICS);
      setMetricsAttriutes(metricsData, FEATURE_NAME_ATTRIBUTE, featureConfig.getFeature());
      setMetricsAttriutes(metricsData, VARIATION_IDENTIFIER_ATTRIBUTE, variation.getIdentifier());
      setMetricsAttriutes(metricsData, VARIATION_VALUE_ATTRIBUTE, variation.getValue());
      if (target.isPrivate()) {
        setMetricsAttriutes(metricsData, TARGET_ATTRIBUTE, ANONYMOUS_TARGET);
      } else {
        setMetricsAttriutes(metricsData, TARGET_ATTRIBUTE, target.getIdentifier());
      }
      setMetricsAttriutes(metricsData, JAR_VERSION, jarVerion);
      setMetricsAttriutes(metricsData, SDK_TYPE, SERVER);

      setMetricsAttriutes(metricsData, SDK_LANGUAGE, "java");
      setMetricsAttriutes(metricsData, SDK_VERSION, jarVerion);
      metrics.addMetricsDataItem(metricsData);
    }

    return metrics;
  }

  private void setMetricsAttriutes(MetricsData metricsData, String key, String value) {
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
