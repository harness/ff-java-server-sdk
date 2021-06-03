package io.harness.cf.client.api;

import static io.harness.cf.client.api.DefaultApiFactory.addAuthHeader;
import static io.harness.cf.model.FeatureConfig.KindEnum.BOOLEAN;
import static io.harness.cf.model.FeatureConfig.KindEnum.INT;
import static io.harness.cf.model.FeatureConfig.KindEnum.JSON;
import static io.harness.cf.model.FeatureConfig.KindEnum.STRING;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.here.oksse.OkSse;
import com.here.oksse.ServerSentEvent;
import io.harness.cf.ApiException;
import io.harness.cf.api.DefaultApi;
import io.harness.cf.client.api.analytics.AnalyticsManager;
import io.harness.cf.client.common.Destroyable;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Prerequisite;
import io.harness.cf.model.Segment;
import io.harness.cf.model.Variation;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
public class CfClient implements Destroyable {

  private final String apiKey;
  private final Config config;
  private final boolean isAnalyticsEnabled;
  @Setter private String jwtToken;
  private String environmentID;
  private String clusterIdentifier;

  private final Cache<String, FeatureConfig> featureCache;
  private final Cache<String, Segment> segmentCache;
  private Evaluator evaluator;
  private final DefaultApi defaultApi;
  private Poller poller;
  private Request sseRequest;
  private SSEListener listener;
  private ServerSentEvent sse;
  private AnalyticsManager analyticsManager;
  @Getter private boolean isInitialized;

  public CfClient(String apiKey) {
    this(apiKey, Config.builder().build());
  }

  public CfClient(String apiKey, Config config) {
    this.apiKey = apiKey;
    this.config = config;

    isAnalyticsEnabled = config.isAnalyticsEnabled();
    featureCache = Caffeine.newBuilder().maximumSize(10000).build();
    segmentCache = Caffeine.newBuilder().maximumSize(10000).build();
    defaultApi =
        DefaultApiFactory.create(
            config.getConfigUrl(),
            config.getConnectionTimeout(),
            config.getReadTimeout(),
            config.getWriteTimeout(),
            config.isDebug());
    isInitialized = false;

    // try to authenticate
    AuthService authService =
        new AuthService(defaultApi, apiKey, this, config.getPollIntervalInSeconds());
    authService.startAsync();
  }

  void init() throws ApiException, CfClientException {
    addAuthHeader(defaultApi, jwtToken);
    environmentID = getEnvironmentID(jwtToken);
    clusterIdentifier = getClusterIdentifier(jwtToken);

    evaluator = new Evaluator(segmentCache);

    initCache(environmentID);
    if (!config.isStreamEnabled()) {
      startPollingMode();
      log.info("Running in polling mode.");
    } else {
      initStreamingMode();
      startSSE();
      log.info("Running in streaming mode.");
    }

    analyticsManager =
        config.isAnalyticsEnabled() ? new AnalyticsManager(environmentID, apiKey, config) : null;
    isInitialized = true;
  }

  private void initCache(String environmentID) throws io.harness.cf.ApiException {
    if (!Strings.isNullOrEmpty(environmentID)) {
      List<FeatureConfig> featureConfigs =
          defaultApi.getFeatureConfig(environmentID, clusterIdentifier);
      if (featureConfigs != null) {
        featureCache.putAll(
            featureConfigs.stream()
                .collect(
                    Collectors.toMap(FeatureConfig::getFeature, featureConfig -> featureConfig)));
      }

      List<Segment> segments = defaultApi.getAllSegments(environmentID, clusterIdentifier);
      if (segments != null) {
        segmentCache.putAll(
            segments.stream()
                .collect(Collectors.toMap(Segment::getIdentifier, segment -> segment)));
      }
    }
  }

  void startPollingMode() {
    poller =
        new Poller(
            defaultApi,
            featureCache,
            segmentCache,
            environmentID,
            clusterIdentifier,
            config.getPollIntervalInSeconds(),
            config.isStreamEnabled(),
            this);
    poller.startAsync();
  }

  private void initStreamingMode() {
    String sseUrl = String.join("", config.getConfigUrl(), "/stream");
    sseRequest =
        new Request.Builder()
            .url(String.format(sseUrl, environmentID))
            .header("Authorization", "Bearer " + jwtToken)
            .header("API-Key", apiKey)
            .build();
    listener =
        new SSEListener(
            defaultApi, featureCache, segmentCache, environmentID, clusterIdentifier, this);
  }

  void startSSE() {

    OkSse okSse = new OkSse();
    sse = okSse.newServerSentEvent(sseRequest, listener);
  }

  public boolean boolVariation(String key, Target target, boolean defaultValue) {
    boolean servedVariation = defaultValue;
    Variation variation = null;
    FeatureConfig featureConfig = featureCache.getIfPresent(key);
    try {
      if (featureConfig == null || featureConfig.getKind() != BOOLEAN) {
        return defaultValue;
      }

      // If pre requisite exists, go ahead till the last dependency else return
      if (!CollectionUtils.isEmpty(featureConfig.getPrerequisites())) {
        boolean result = checkPreRequisite(featureConfig, target);
        if (!result) {
          servedVariation = Boolean.parseBoolean(featureConfig.getOffVariation());
          return servedVariation;
        }
      }
      variation = evaluator.evaluate(featureConfig, target);
      servedVariation = Boolean.parseBoolean((String) variation.getValue());
      return servedVariation;
    } catch (Exception e) {
      log.error("err", e);
      return defaultValue;
    } finally {
      if (!target.isPrivate()
          && target.isValid()
          && isAnalyticsEnabled
          && analyticsManager != null
          && featureConfig != null
          && variation != null) {
        analyticsManager.pushToQueue(target, featureConfig, variation);
      }
    }
  }

  public String stringVariation(String key, Target target, String defaultValue) {
    String stringVariation = defaultValue;
    Variation variation = null;
    FeatureConfig featureConfig = featureCache.getIfPresent(key);
    try {
      if (featureConfig == null || featureConfig.getKind() != STRING) {
        return defaultValue;
      }

      // If pre requisite exists, go ahead till the last dependency else return
      if (!CollectionUtils.isEmpty(featureConfig.getPrerequisites())) {
        boolean result = checkPreRequisite(featureConfig, target);
        if (!result) {
          stringVariation = featureConfig.getOffVariation();
          return stringVariation;
        }
      }
      variation = evaluator.evaluate(featureConfig, target);
      stringVariation = (String) variation.getValue();
      return stringVariation;
    } catch (Exception e) {
      log.error("err", e);
      return defaultValue;
    } finally {
      if (!target.isPrivate()
          && target.isValid()
          && isAnalyticsEnabled
          && analyticsManager != null
          && featureConfig != null
          && variation != null) {
        analyticsManager.pushToQueue(target, featureConfig, variation);
      }
    }
  }

  public double numberVariation(String key, Target target, int defaultValue) {
    double numberVariation = defaultValue;
    Variation variation = null;
    FeatureConfig featureConfig = featureCache.getIfPresent(key);
    if (featureConfig == null || featureConfig.getKind() != INT) {
      return defaultValue;
    }

    try {
      // If pre requisite exists, go ahead till the last dependency else return
      if (!CollectionUtils.isEmpty(featureConfig.getPrerequisites())) {
        boolean result = checkPreRequisite(featureConfig, target);
        if (!result) {
          numberVariation = Integer.parseInt(featureConfig.getOffVariation());
          return numberVariation;
        }
      }
      variation = evaluator.evaluate(featureConfig, target);
      numberVariation = Integer.parseInt((String) variation.getValue());
      return numberVariation;
    } catch (Exception e) {
      log.error("err", e);
      return defaultValue;
    } finally {
      if (!target.isPrivate()
          && target.isValid()
          && isAnalyticsEnabled
          && analyticsManager != null
          && featureConfig != null
          && variation != null) {
        analyticsManager.pushToQueue(target, featureConfig, variation);
      }
    }
  }

  public JsonObject jsonVariation(String key, Target target, JsonObject defaultValue) {
    JsonObject jsonObject = defaultValue;
    Variation variation = null;
    FeatureConfig featureConfig = featureCache.getIfPresent(key);
    try {
      if (featureConfig == null || featureConfig.getKind() != JSON) {
        return defaultValue;
      }

      // If pre requisite exists, go ahead till the last dependency else return
      if (!CollectionUtils.isEmpty(featureConfig.getPrerequisites())) {
        boolean result = checkPreRequisite(featureConfig, target);
        if (!result) {
          JsonObject obj = new JsonObject();
          jsonObject = obj.getAsJsonObject(featureConfig.getOffVariation());
          return jsonObject;
        }
      }
      variation = evaluator.evaluate(featureConfig, target);
      jsonObject = new Gson().fromJson((String) variation.getValue(), JsonObject.class);
      return jsonObject;
    } catch (Exception e) {
      log.error("err", e);
      return defaultValue;
    } finally {
      if (!target.isPrivate()
          && target.isValid()
          && isAnalyticsEnabled
          && analyticsManager != null
          && featureConfig != null
          && variation != null) {
        analyticsManager.pushToQueue(target, featureConfig, variation);
      }
    }
  }

  private boolean checkPreRequisite(FeatureConfig parentFeatureConfig, Target target)
      throws Exception {
    boolean result = true;
    List<Prerequisite> prerequisites = parentFeatureConfig.getPrerequisites();
    if (!CollectionUtils.isEmpty(prerequisites)) {
      log.info(
          "Checking pre requisites {} of parent feature {}",
          prerequisites,
          parentFeatureConfig.getFeature());
      for (Prerequisite pqs : prerequisites) {
        String preReqFeature = pqs.getFeature();
        FeatureConfig preReqFeatureConfig = featureCache.getIfPresent(preReqFeature);
        if (preReqFeatureConfig == null) {
          log.error(
              "Could not retrieve the pre requisite details of feature flag :{}",
              preReqFeatureConfig.getFeature());
        }

        // Pre requisite variation value evaluated below
        Object preReqEvaluatedVariation =
            evaluator.evaluate(preReqFeatureConfig, target).getValue();
        log.info(
            "Pre requisite flag {} has variation {} for target {}",
            preReqFeatureConfig.getFeature(),
            preReqEvaluatedVariation,
            target);

        // Compare if the pre requisite variation is a possible valid value of
        // the pre requisite FF
        List<String> validPreReqVariations = pqs.getVariations();
        log.info(
            "Pre requisite flag {} should have the variations {}",
            preReqFeatureConfig.getFeature(),
            validPreReqVariations);
        if (!validPreReqVariations.contains(preReqEvaluatedVariation.toString())) {
          return false;
        } else {
          result = checkPreRequisite(preReqFeatureConfig, target);
        }
      }
    }
    return result;
  }

  public static String getEnvironmentID(String jwtToken) {
    int i = jwtToken.lastIndexOf('.');
    String unsignedJwt = jwtToken.substring(0, i + 1);
    Jwt<Header, Claims> untrusted = Jwts.parser().parseClaimsJwt(unsignedJwt);
    jwtToken = (String) untrusted.getBody().get("environment");
    return jwtToken;
  }

  public static String getClusterIdentifier(String jwtToken) {
    int i = jwtToken.lastIndexOf('.');
    String unsignedJwt = jwtToken.substring(0, i + 1);
    Jwt<Header, Claims> untrusted = Jwts.parser().parseClaimsJwt(unsignedJwt);
    jwtToken = (String) untrusted.getBody().get("clusterIdentifier");
    return jwtToken;
  }

  void stopPoller() {

    if (poller != null && poller.isRunning()) {

      log.info("Stopping poller.");
      poller.stopAsync();
    }
  }

  @SneakyThrows
  @Override
  public void destroy() {

    if (analyticsManager != null) {

      analyticsManager.destroy();
    }

    stopPoller();
    if (sse != null) {

      sse.close();
    }
    featureCache.cleanUp();
  }
}
