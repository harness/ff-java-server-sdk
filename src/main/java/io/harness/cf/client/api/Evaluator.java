package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sangupta.murmur.Murmur3;
import com.sangupta.murmur.MurmurConstants;
import io.harness.cf.client.common.SdkCodes;
import io.harness.cf.client.common.StringUtils;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.MDC;

@Slf4j
public class Evaluator implements Evaluation {

  public static final int ONE_HUNDRED = 100;

  private final Query query;

  public Evaluator(Query query) {
    this.query = query;
  }

  protected Optional<Object> getAttrValue(Target target, @NonNull String attribute) {
    if (StringUtils.isNullOrEmpty(attribute)) {
      log.debug("Attribute is empty");
      return Optional.empty();
    }

    if (target == null) {
      log.debug("Target is null");
      return Optional.empty();
    }

    switch (attribute) {
      case "identifier":
        return Optional.of(target.getIdentifier());
      case "name":
        return Optional.ofNullable(target.getName());
      default:
        if (target.getAttributes() != null) {
          log.debug("Checking attributes field {}", attribute);
          return Optional.ofNullable(target.getAttributes().get(attribute));
        }
    }
    log.error("Attribute {} does not exist", attribute);
    return Optional.empty();
  }

  protected Optional<Variation> findVariation(
      @NonNull List<Variation> variations, String identifier) {
    if (identifier == null || CollectionUtils.isEmpty(variations)) {
      log.debug("Empty identifier {} or variations {} occurred", identifier, variations);
      return Optional.empty();
    }
    Optional<Variation> variation =
        variations.stream().filter(v -> v.getIdentifier().equals(identifier)).findFirst();
    log.debug("Variation {} found in variations {}", identifier, variations);
    return variation;
  }

  protected int getNormalizedNumber(@NonNull Object property, @NonNull String bucketBy) {
    byte[] value = String.join(":", bucketBy, property.toString()).getBytes();
    long hasher = Murmur3.hash_x86_32(value, value.length, MurmurConstants.UINT_MASK);
    int result = (int) (hasher % Evaluator.ONE_HUNDRED) + 1;
    log.debug("normalized number for {} = {}", Arrays.toString(value), result);
    return result;
  }

  protected boolean isEnabled(Target target, String bucketBy, int percentage) {
    final Optional<Object> attrValue = getAttrValue(target, bucketBy);
    if (!attrValue.isPresent()) {
      log.debug("Returns false attribute not present {}", bucketBy);
      return false;
    }
    int bucketId = getNormalizedNumber(attrValue.get(), bucketBy);
    return percentage > 0 && bucketId <= percentage;
  }

  protected Optional<String> evaluateDistribution(Distribution distribution, Target target) {
    if (distribution == null) {
      log.debug("Distribution is empty");
      return Optional.empty();
    }

    String variation = "";
    int totalPercentage = 0;
    for (WeightedVariation weightedVariation : distribution.getVariations()) {
      variation = weightedVariation.getVariation();
      log.debug("Checking variation {}", variation);
      totalPercentage += weightedVariation.getWeight();
      if (isEnabled(target, distribution.getBucketBy(), totalPercentage)) {
        log.debug("Enabled for distribution {}", distribution);
        return Optional.of(weightedVariation.getVariation());
      }
    }
    log.debug("Variation of distribution evaluation {}", variation);
    return Optional.of(variation);
  }

  protected boolean evaluateClause(Clause clause, Target target) {
    if (clause == null) {
      log.debug("Clause is empty");
      return false;
    }
    // operator is required
    final String operator = clause.getOp();
    if (operator.isEmpty()) {
      log.debug("Clause {} operator is empty!", clause);
      return false;
    }

    if (operator.equals(SEGMENT_MATCH)) {
      log.debug("Clause operator is {}, evaluate on segment", operator);
      return isTargetIncludedOrExcludedInSegment(clause.getValues(), target);
    }

    if (clause.getValues().isEmpty()) {
      log.debug("Clause values is empty");
      return false;
    }

    String value = clause.getValues().get(0);
    Optional<Object> attrValue = getAttrValue(target, clause.getAttribute());

    if (!attrValue.isPresent()) {
      log.debug("AttrValue is empty on clause {}", clause);
      return false;
    }

    String object = attrValue.get().toString();
    log.debug("evaluate clause with object {} operator {} and value {}", object, operator, value);
    switch (operator) {
      case STARTS_WITH:
        return object.startsWith(value);
      case ENDS_WITH:
        return object.endsWith(value);
      case MATCH:
        return object.matches(value);
      case CONTAINS:
        return object.contains(value);
      case EQUAL:
        return object.equalsIgnoreCase(value);
      case EQUAL_SENSITIVE:
        return object.equals(value);
      case IN:
        return clause.getValues().contains(object);
      default:
        log.debug("operator {} not found", operator);
        return false;
    }
  }

  protected boolean evaluateClauses(List<Clause> clauses, Target target) {
    for (Clause clause : clauses) {
      if (evaluateClause(clause, target)) {
        // If any clause returns true we return true - rules being treated as OR
        log.debug("Successful evaluation of clause {}", clause);
        return true;
      }
    }
    // All clauses conditions failed so return false
    log.debug("All clauses {} failed", clauses);
    return false;
  }

  /**
   * isTargetIncludedOrExcludedInSegment determines if the given target is included by a segment
   *
   * @param segmentList a list of segments
   * @param target the target to check if its included
   * @return true if the target is included in the segment via rules
   */
  private boolean isTargetIncludedOrExcludedInSegment(List<String> segmentList, Target target) {
    for (String segmentIdentifier : segmentList) {
      final Optional<Segment> optionalSegment = query.getSegment(segmentIdentifier);
      if (optionalSegment.isPresent()) {
        final Segment segment = optionalSegment.get();
        // Should Target be excluded - if in excluded list we return false
        if (isTargetInList(target, segment.getExcluded())) {
          log.debug("Target excluded from segment {} via exclude list", segment.getIdentifier());
          return false;
        }

        // Should Target be included - if in included list we return true
        if (isTargetInList(target, segment.getIncluded())) {
          log.debug("Target included in segment {} via include list", segment.getIdentifier());
          return true;
        }

        // Should Target be included via segment rules
        List<Clause> rules = segment.getRules();
        if ((rules != null) && !rules.isEmpty() && evaluateClauses(rules, target)) {
          log.debug("Target included in segment {} via rules", segment.getName());
          return true;
        }
      }
    }
    log.debug("Target groups empty return false");
    return false;
  }

  protected boolean evaluateRule(ServingRule servingRule, Target target) {
    return this.evaluateClauses(servingRule.getClauses(), target);
  }

  protected Optional<String> evaluateRules(List<ServingRule> servingRules, Target target) {
    if (target == null || servingRules == null) {
      log.debug("There is no target or serving rule");
      return Optional.empty();
    }

    final List<ServingRule> rules = new ArrayList<>(servingRules);

    log.debug("Sorting serving rules {}", rules);
    rules.sort(Comparator.comparing(ServingRule::getPriority));
    log.debug("Sorted serving rules {}", rules);
    for (ServingRule rule : rules) {
      // if evaluation is false just continue to next rule
      if (!this.evaluateRule(rule, target)) {
        log.debug("Unsuccessful evaluation of rule {} continue to next rule", rule);
        continue;
      }

      // rule matched, check if there is distribution
      Distribution distribution = rule.getServe().getDistribution();
      if (distribution != null) {
        log.debug("Evaluate distribution {}", distribution);
        return evaluateDistribution(distribution, target);
      }

      // rule matched, here must be variation if distribution is undefined or null
      String identifier = rule.getServe().getVariation();
      if (identifier != null) {
        log.debug("Return rule variation identifier {}", identifier);
        return Optional.of(identifier);
      }
    }
    log.debug("All rules failed, return empty identifier");
    return Optional.empty();
  }

  protected Optional<String> evaluateVariationMap(
      @NonNull List<VariationMap> variationMaps, Target target) {
    if (target == null) {
      log.debug("Target is null");
      return Optional.empty();
    }
    for (VariationMap variationMap : variationMaps) {
      List<TargetMap> targets = variationMap.getTargets();
      if (targets != null) {
        Optional<TargetMap> found =
            targets.stream()
                .filter(
                    t -> {
                      if (t.getIdentifier() != null)
                        return t.getIdentifier().equals(target.getIdentifier());
                      return false;
                    })
                .findFirst();
        if (found.isPresent()) {
          log.debug("Evaluate variationMap with result {}", variationMap.getVariation());
          return Optional.of(variationMap.getVariation());
        }
      }

      List<String> segmentIdentifiers = variationMap.getTargetSegments();
      if (segmentIdentifiers != null
          && isTargetIncludedOrExcludedInSegment(segmentIdentifiers, target)) {
        log.debug(
            "Evaluate variationMap with segment identifiers {} and return {}",
            segmentIdentifiers,
            variationMap.getVariation());
        return Optional.of(variationMap.getVariation());
      }
    }
    return Optional.empty();
  }

  protected Optional<Variation> evaluateFlag(@NonNull FeatureConfig featureConfig, Target target) {
    Optional<String> variation = Optional.of(featureConfig.getOffVariation());
    if (featureConfig.getState() == FeatureState.ON) {
      variation = Optional.empty();
      if (featureConfig.getVariationToTargetMap() != null)
        variation = evaluateVariationMap(featureConfig.getVariationToTargetMap(), target);
      if (!variation.isPresent()) variation = evaluateRules(featureConfig.getRules(), target);
      if (!variation.isPresent())
        variation = evaluateDistribution(featureConfig.getDefaultServe().getDistribution(), target);
      if (!variation.isPresent())
        variation = Optional.ofNullable(featureConfig.getDefaultServe().getVariation());
    }
    if (variation.isPresent()) return findVariation(featureConfig.getVariations(), variation.get());
    log.debug("No variation found return empty");
    return Optional.empty();
  }

  protected boolean checkPreRequisite(FeatureConfig parentFeatureConfig, Target target) {
    List<Prerequisite> prerequisites = parentFeatureConfig.getPrerequisites();
    if (!CollectionUtils.isEmpty(prerequisites)) {
      log.debug(
          "Checking pre requisites {} of parent feature {}",
          prerequisites,
          parentFeatureConfig.getFeature());
      for (Prerequisite pqs : prerequisites) {
        String preReqFeature = pqs.getFeature();
        Optional<FeatureConfig> preReqFeatureConfig = query.getFlag(preReqFeature);
        if (!preReqFeatureConfig.isPresent()) {
          log.error(
              "Could not retrieve the pre requisite details of feature flag :{}", preReqFeature);
          return true;
        }

        // Pre requisite variation value evaluated below
        Optional<Variation> preReqEvaluatedVariation =
            evaluateFlag(preReqFeatureConfig.get(), target);
        if (!preReqEvaluatedVariation.isPresent()) {
          log.error(
              "Could not evaluate the prerequisite details of feature flag :{}", preReqFeature);
          return true;
        }
        log.debug(
            "Pre requisite flag {} has variation {} for target {}",
            preReqFeatureConfig.get().getFeature(),
            preReqEvaluatedVariation.get(),
            target);

        // Compare if the pre requisite variation is a possible valid value of
        // the pre requisite FF
        List<String> validPreReqVariations = pqs.getVariations();
        log.debug(
            "Pre requisite flag {} should have the variations {}",
            preReqFeatureConfig.get().getFeature(),
            validPreReqVariations);
        if (validPreReqVariations.stream()
            .noneMatch(
                element -> element.contains(preReqEvaluatedVariation.get().getIdentifier()))) {
          return false;
        } else {
          if (!checkPreRequisite(preReqFeatureConfig.get(), target)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public Optional<Variation> evaluate(
      String identifier,
      Target target,
      FeatureConfig.KindEnum expected,
      FlagEvaluateCallback callback) {
    final String targetKey = "target";
    final String flagKey = "flag";
    MDC.put(flagKey, identifier);
    MDC.put(targetKey, "_no_target");
    if (target != null) {
      MDC.put(targetKey, target.getIdentifier());
    }
    try {
      Optional<FeatureConfig> flag = query.getFlag(identifier);
      if (!flag.isPresent() || flag.get().getKind() != expected) {
        return Optional.empty();
      }

      if (!CollectionUtils.isEmpty(flag.get().getPrerequisites())) {
        boolean prereq = checkPreRequisite(flag.get(), target);
        if (!prereq) {
          return findVariation(flag.get().getVariations(), flag.get().getOffVariation());
        }
      }

      final Optional<Variation> variation = evaluateFlag(flag.get(), target);
      if (variation.isPresent()) {
        if (callback != null) {
          callback.processEvaluation(flag.get(), target, variation.get());
        }
        return variation;
      }
    } finally {
      MDC.remove(flagKey);
      MDC.remove(targetKey);
    }
    return Optional.empty();
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

  public boolean boolVariation(
      String identifier, Target target, boolean defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.BOOLEAN, callback);

    if (variation.isPresent()) {
      return Boolean.parseBoolean(variation.get().getValue());
    }

    SdkCodes.warnDefaultVariationServed(identifier, target, String.valueOf(defaultValue));
    return defaultValue;
  }

  public String stringVariation(
      String identifier, Target target, String defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.STRING, callback);

    if (variation.isPresent()) {
      return variation.get().getValue();
    }

    SdkCodes.warnDefaultVariationServed(identifier, target, defaultValue);
    return defaultValue;
  }

  public double numberVariation(
      String identifier, Target target, double defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.INT, callback);

    if (variation.isPresent()) {
      return Double.parseDouble(variation.get().getValue());
    }

    SdkCodes.warnDefaultVariationServed(identifier, target, String.valueOf(defaultValue));
    return defaultValue;
  }

  public JsonObject jsonVariation(
      String identifier, Target target, JsonObject defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.JSON, callback);

    if (variation.isPresent()) {
      return new Gson().fromJson(variation.get().getValue(), JsonObject.class);
    }

    SdkCodes.warnDefaultVariationServed(identifier, target, defaultValue.toString());
    return defaultValue;
  }
}
