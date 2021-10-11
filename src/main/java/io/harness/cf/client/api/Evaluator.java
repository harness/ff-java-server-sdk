package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;
import static java.lang.String.format;

import com.github.benmanes.caffeine.cache.Cache;
import io.harness.cf.client.Evaluation;
import io.harness.cf.client.api.rules.DistributionProcessor;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.lang.reflect.Field;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Evaluator implements Evaluation {

  private final Cache<String, Segment> segmentCache;

  public Evaluator(Cache<String, Segment> segmentCache) {

    this.segmentCache = segmentCache;
  }

  @Override
  public Variation evaluate(FeatureConfig featureConfig, Target target) throws CfClientException {

    String servedVariation = featureConfig.getOffVariation();
    if (featureConfig.getState() == FeatureState.OFF) {
      return getVariation(featureConfig.getVariations(), servedVariation);
    }

    servedVariation = processVariationMap(target, featureConfig.getVariationToTargetMap());
    if (servedVariation != null) {
      return getVariation(featureConfig.getVariations(), servedVariation);
    }

    servedVariation = processRules(featureConfig, target);
    if (servedVariation != null) {
      return getVariation(featureConfig.getVariations(), servedVariation);
    }

    Serve defaultServe = featureConfig.getDefaultServe();
    servedVariation = processDefaultServe(defaultServe, target);

    return getVariation(featureConfig.getVariations(), servedVariation);
  }

  private String processVariationMap(Target target, List<VariationMap> variationMaps)
      throws CfClientException {

    if (variationMaps == null) {
      return null;
    }
    for (VariationMap variationMap : variationMaps) {

      List<TargetMap> targets = variationMap.getTargets();

      if (targets != null) {
        for (TargetMap targetMap : targets) {
          if (targetMap.getIdentifier() != null
              && targetMap.getIdentifier().contains(target.getIdentifier())) {
            return variationMap.getVariation();
          }
        }
      }

      List<String> segmentIdentifiers = variationMap.getTargetSegments();
      if (segmentIdentifiers != null) {
        if (isTargetIncludedBySegment(segmentIdentifiers, target)) {
          return variationMap.getVariation();
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
    for (ServingRule servingRule : Objects.requireNonNull(servingRules)) {
      String servedVariation = processServingRule(servingRule, target);
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
    return Optional.of(clause.getNegate()).orElse(false) != result;
  }

  /**
   * isTargetInList determines if the specified target is in the list of targets
   *
   * @param target a target that we want to check if it is in the list
   * @param listOfTargets a list of targets
   * @return true if target is in listOfTargets otherwise returns false
   */
  private boolean isTargetInList(Target target, List<io.harness.cf.model.Target> listOfTargets) {
    if (listOfTargets != null) {
      for (io.harness.cf.model.Target includedTarget : listOfTargets) {
        if (includedTarget.getIdentifier().contains(target.getIdentifier())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * isTargetIncludedBySegment determines if the given target is included by a segment
   *
   * @param segmentList a list of segments
   * @param target the target to check if its included
   * @return true if the target is included in the segment via rules
   */
  private boolean isTargetIncludedBySegment(List<String> segmentList, Target target)
      throws CfClientException {

    for (String segmentIdentifier : segmentList) {
      Segment segment = segmentCache.getIfPresent(segmentIdentifier);
      if (segment != null) {

        // Should Target be excluded - if in excluded list we return false
        if (isTargetInList(target, segment.getExcluded())) {
          log.debug(
              format(
                  "Target %s excluded from segment %s via exclude list",
                  target.getName(), segment.getName()));
          return false;
        }

        // Should Target be included - if in included list we return true
        if (isTargetInList(target, segment.getIncluded())) {
          log.debug(
              format(
                  "Target %s included in segment %s via include list",
                  target.getName(), segment.getName()));
          return true;
        }

        // Should Target be included via segment rules
        if ((segment.getRules() != null) && !segment.getRules().isEmpty()) {
          for (Clause rule : segment.getRules()) {
            if (compare(rule.getValues(), target, rule)) {
              log.debug(
                  format(
                      "Target %s included in segment %s via rules",
                      target.getName(), segment.getName()));
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean compare(List<String> value, Target target, Clause clause)
      throws CfClientException {
    String operator = clause.getOp();
    String object;
    Object attrValue;
    try {
      attrValue = getAttrValue(target, clause.getAttribute());
    } catch (CfClientException e) {
      attrValue = "";
    }

    object = attrValue.toString();

    String v = (value).get(0);
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
        return isTargetIncludedBySegment(value, target);
      default:
        return false;
    }
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

  private Variation getVariation(List<Variation> variations, String variationIdentifier)
      throws CfClientException {

    for (Variation variation : variations) {
      if (variationIdentifier.equals(variation.getIdentifier())) {
        return variation;
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
