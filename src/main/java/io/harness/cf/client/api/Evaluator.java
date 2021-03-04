package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;
import static io.harness.cf.client.api.Operators.SEGMENT_MATCH;
import static java.lang.String.format;

import com.github.benmanes.caffeine.cache.Cache;
import io.harness.cf.client.api.rules.DistributionProcessor;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.lang.reflect.Field;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Evaluator {
  private final Cache<String, Segment> segmentCache;

  public Evaluator(Cache<String, Segment> segmentCache) {
    this.segmentCache = segmentCache;
  }

  public Object evaluate(FeatureConfig featureConfig, Target target) throws CfClientException {
    String servedVariation = featureConfig.getOffVariation();
    if (featureConfig.getState() == FeatureState.OFF) {
      return servedVariation;
    }

    servedVariation = processVariationMap(target, featureConfig.getVariationToTargetMap());
    if (servedVariation != null) {
      return getVariationValue(featureConfig.getVariations(), servedVariation);
    }

    servedVariation = processRules(featureConfig, target);
    if (servedVariation != null) {
      return getVariationValue(featureConfig.getVariations(), servedVariation);
    }

    Serve defaultServe = featureConfig.getDefaultServe();
    servedVariation = processDefaultServe(defaultServe, target);

    return getVariationValue(featureConfig.getVariations(), servedVariation);
  }

  private String processVariationMap(Target target, List<VariationMap> variationMaps) {
    if (variationMaps == null) {
      return null;
    }
    for (VariationMap variationMap : variationMaps) {
      List<String> targets = variationMap.getTargets();
      if (targets != null && targets.contains(target.getIdentifier())) {
        return variationMap.getVariation();
      }
      List<String> segmentIdentifiers = variationMap.getTargetSegments();
      if (segmentIdentifiers != null) {
        for (String segmentIdentifier : segmentIdentifiers) {
          Segment segment = segmentCache.getIfPresent(segmentIdentifier);
          if (segment != null) {
            List<String> includedTargets = segment.getIncluded();
            if (includedTargets != null && includedTargets.contains(target.getIdentifier())) {
              return variationMap.getVariation();
            }
          }
        }
      }
    }
    return null;
  }

  private String processRules(FeatureConfig featureConfig, Target target) throws CfClientException {
    List<ServingRule> originalServingRules = featureConfig.getRules();
    ArrayList<ServingRule> servingRules =
        new ArrayList<>(Optional.ofNullable(originalServingRules).orElse(new ArrayList<>()));
    servingRules.sort(Comparator.comparing(ServingRule::getPriority));
    String servedVariation = null;
    for (ServingRule servingRule : Objects.requireNonNull(servingRules)) {
      servedVariation = processServingRule(servingRule, target);
      if (servedVariation != null) {
        return servedVariation;
      }
    }
    return null;
  }

  private String processServingRule(ServingRule servingRule, Target target)
      throws CfClientException {
    for (Clause clause : Objects.requireNonNull(servingRule.getClauses())) {
      if (!process(clause, target)) { // check if the target match the clause
        return null;
      }
    }

    Serve serve = servingRule.getServe();
    String servedVariation;
    if (serve.getVariation() != null) {
      servedVariation = serve.getVariation();
    } else {
      DistributionProcessor distributionProcessor =
          new DistributionProcessor(servingRule.getServe());
      servedVariation = distributionProcessor.loadKeyName(target);
    }
    return servedVariation;
  }

  private boolean process(Clause clause, Target target) throws CfClientException {
    boolean result = compare(clause.getValues(), target, clause);
    return Optional.ofNullable(clause.getNegate()).orElse(false) != result;
  }

  private boolean compare(List value, Target target, Clause clause) throws CfClientException {
    String operator = clause.getOp();
    String object = null;
    Object attrValue = null;
    try {
      attrValue = getAttrValue(target, clause.getAttribute());
    } catch (CfClientException e) {
      attrValue = "";
    }
    object = (String) attrValue;

    if (clause.getValues() == null) {
      throw new CfClientException("The clause is missing values");
    }

    String v = (String) (value).get(0);
    switch (operator) {
      case STARTS_WITH:
        return object.startsWith(v);
      case ENDS_WITH:
        return object.endsWith(v);
      case MATCH:
        return object.matches(v);
      case CONTAINS:
        return object.contains(v);
      case EQUAL:
        return object.equalsIgnoreCase(v);
      case EQUAL_SENSITIVE:
        return object.equals(v);
      case IN:
        return value.contains(object);
      case SEGMENT_MATCH:
        for (String segmentIdentifier : (List<String>) value) {
          Segment segment = segmentCache.getIfPresent(segmentIdentifier);
          if (segment != null) {
            List<String> includedTargets = segment.getIncluded();
            if (includedTargets != null && includedTargets.contains(target.getIdentifier())) {
              return true;
            }
            if ((segment.getRules() != null) && !segment.getRules().isEmpty()) {
              for (Clause rule : segment.getRules()) {
                try {
                  object = (String) getAttrValue(target, rule.getAttribute());
                } catch (CfClientException e) {
                  object = "";
                }
                if (object != null) {
                  List<String> values = new ArrayList<>();
                  values.add(object);
                  boolean returnValue = compare(values, target, rule);
                  if (returnValue) {
                    return returnValue;
                  }
                }
              }
            }
          }
        }
        return false;
      default:
        return false;
    }
  }

  private boolean in(String[] conditions, String object) {
    for (String condition : conditions) {
      if (object.equalsIgnoreCase(condition)) {
        return true;
      }
    }
    return false;
  }

  private String processDefaultServe(Serve defaultServe, Target target) throws CfClientException {
    if (defaultServe == null) {
      throw new CfClientException("The serving rule is missing default serve.");
    }
    String servedVariation;
    if (defaultServe.getVariation() != null) {
      servedVariation = defaultServe.getVariation();
    } else if (defaultServe.getDistribution() != null) {
      DistributionProcessor distributionProcessor = new DistributionProcessor(defaultServe);
      servedVariation = distributionProcessor.loadKeyName(target);
    } else {
      throw new CfClientException("The default serving rule is invalid.");
    }
    return servedVariation;
  }

  private String getVariationValue(List<Variation> variations, String variationIdentifier)
      throws CfClientException {
    for (Variation variation : variations) {
      if (variationIdentifier.equals(variation.getIdentifier())) {
        return variation.getValue();
      }
    }
    throw new CfClientException(format("Invalid variation identifier %s.", variationIdentifier));
  }

  public static Object getAttrValue(Target target, @Nonnull String attribute)
      throws CfClientException {

    Field field;
    try {
      field = Target.class.getDeclaredField(attribute);
      field.setAccessible(true);
      return field.get(target);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      Map<String, Object> customFields = target.getAttributes();
      if (customFields != null) {
        for (Map.Entry<String, Object> entry : target.getAttributes().entrySet()) {
          if (entry.getKey().equals(attribute)) {
            return entry.getValue();
          }
        }
      }
    }
    throw new CfClientException(format("The attribute %s does not exist", attribute));
  }
}
