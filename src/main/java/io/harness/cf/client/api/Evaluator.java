package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sangupta.murmur.Murmur3;
import com.sangupta.murmur.MurmurConstants;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.MDC;

@Slf4j
class Evaluator implements Evaluation {

  public static final int ONE_HUNDRED = 100;

  private final Query query;

  public Evaluator(Query query) {
    this.query = query;
  }

  protected Optional<Object> getAttrValue(Target target, @NonNull String attribute) {
    log.debug("getAttrValue started with {}", attribute);
    if (Strings.isNullOrEmpty(attribute)) {
      log.debug("getAttrValue -> attribute is empty");
      return Optional.empty();
    }
    try {
      Field field = Target.class.getDeclaredField(attribute);
      field.setAccessible(true);
      return Optional.of(field.get(target));
    } catch (NoSuchFieldException | IllegalAccessException e) {
      if (target.getAttributes() != null) {
        log.debug("getAttrValue -> checking attributes field {}", attribute);
        return Optional.of(target.getAttributes().get(attribute));
      }
    }
    log.error("getAttrValue -> attribute {} does not exist", attribute);
    return Optional.empty();
  }

  protected Optional<Variation> findVariation(
      @NonNull List<Variation> variations, String identifier) {
    log.debug("run findVariation with {}, {}", variations, identifier);
    if (identifier == null || CollectionUtils.isEmpty(variations)) {
      log.debug(
          "findVariation -> empty identifier {} or variations {} occurred", identifier, variations);
      return Optional.empty();
    }
    log.debug("findVariation -> finding variation {} in variations {}", identifier, variations);
    return variations.stream().filter(v -> v.getIdentifier().equals(identifier)).findFirst();
  }

  protected int getNormalizedNumber(@NonNull Object property, @NonNull String bucketBy) {
    log.debug("run getNormalizedNumber with {}, {}", property, bucketBy);
    byte[] value = String.join(":", bucketBy, property.toString()).getBytes();
    long hasher = Murmur3.hash_x86_32(value, value.length, MurmurConstants.UINT_MASK);
    int result = (int) (hasher % Evaluator.ONE_HUNDRED) + 1;
    log.debug("getNormalizedNumber -> for {} = {}", Arrays.toString(value), result);
    return result;
  }

  protected boolean isEnabled(Target target, String bucketBy, int percentage) {
    log.debug("run isEnabled with {}, {}", bucketBy, percentage);
    final Optional<Object> attrValue = getAttrValue(target, bucketBy);
    if (!attrValue.isPresent()) {
      log.debug("isEnabled -> returns false attribute not present {}", bucketBy);
      return false;
    }
    int bucketId = getNormalizedNumber(attrValue.get(), bucketBy);
    boolean result = percentage > 0 && bucketId <= percentage;
    log.debug("isEnabled -> result {} for bucketBy {}", result, bucketBy);
    return result;
  }

  protected Optional<String> evaluateDistribution(Distribution distribution, Target target) {
    log.debug("run evaluateDistribution with {}", distribution);
    if (distribution == null) {
      log.debug("evaluateDistribution -> distribution is empty");
      return Optional.empty();
    }

    String variation = "";
    for (WeightedVariation weightedVariation : distribution.getVariations()) {
      variation = weightedVariation.getVariation();
      log.debug("evaluateDistribution -> checking variation {}", variation);
      if (isEnabled(target, distribution.getBucketBy(), weightedVariation.getWeight())) {
        log.debug("evaluateDistribution -> enabled on distribution {}", distribution);
        return Optional.of(weightedVariation.getVariation());
      }
    }
    log.debug("evaluateDistribution -> result variation = {}", variation);
    return Optional.of(variation);
  }

  protected boolean evaluateClause(Clause clause, Target target) {
    log.debug("run evaluateClause with {}", clause);
    if (clause == null) {
      log.debug("evaluateClause -> clause is empty");
      return false;
    }
    // operator is required
    final String operator = clause.getOp();
    if (operator.isEmpty()) {
      log.debug("evaluateClause -> clause {} operator is empty!", clause);
      return false;
    }

    if (operator.equals(SEGMENT_MATCH)) {
      log.debug("evaluateClause -> clause operator is {}, run evaluation on segment", operator);
      return isTargetIncludedOrExcludedInSegment(clause.getValues(), target);
    }

    if (clause.getValues().isEmpty()) {
      log.debug("evaluateClause -> clause values is empty");
      return false;
    }

    String value = clause.getValues().get(0);
    Optional<Object> attrValue = getAttrValue(target, clause.getAttribute());

    if (!attrValue.isPresent()) {
      log.debug("evaluateClause -> attrValue is empty on clause {}", clause);
      return false;
    }

    String object = attrValue.get().toString();
    log.debug("evaluateClause -> {} {} {}", object, operator, value);
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
        return value.contains(object);
      default:
        log.debug("evaluateClause -> {} not found", operator);
        return false;
    }
  }

  protected boolean evaluateClauses(List<Clause> clauses, Target target) {
    log.debug("run evaluateClauses with {}", clauses);
    for (Clause clause : clauses) {
      if (!evaluateClause(clause, target)) {
        log.debug("evaluateClauses ->  unsuccessfully evaluation of clause {}", clause);
        return false;
      }
    }
    log.debug("evaluateClauses -> all clauses {} passed", clauses);
    return true;
  }

  /**
   * isTargetIncludedOrExcludedInSegment determines if the given target is included by a segment
   *
   * @param segmentList a list of segments
   * @param target the target to check if its included
   * @return true if the target is included in the segment via rules
   */
  private boolean isTargetIncludedOrExcludedInSegment(List<String> segmentList, Target target) {
    log.debug("run isTargetIncludedOrExcludedInSegment with {}", segmentList);
    for (String segmentIdentifier : segmentList) {
      final Optional<Segment> optionalSegment = query.getSegment(segmentIdentifier);
      if (optionalSegment.isPresent()) {
        final Segment segment = optionalSegment.get();
        // Should Target be excluded - if in excluded list we return false
        if (isTargetInList(target, segment.getExcluded())) {
          log.debug(
              "isTargetIncludedOrExcludedInSegment -> target excluded from segment {} via exclude list",
              segment.getIdentifier());
          return false;
        }

        // Should Target be included - if in included list we return true
        if (isTargetInList(target, segment.getIncluded())) {
          log.debug(
              "isTargetIncludedOrExcludedInSegment -> target included in segment {} via include list",
              segment.getIdentifier());
          return true;
        }

        // Should Target be included via segment rules
        List<Clause> rules = segment.getRules();
        if ((rules != null) && !rules.isEmpty() && evaluateClauses(rules, target)) {
          log.debug(
              "isTargetIncludedOrExcludedInSegment -> target included in segment {} via rules",
              segment.getName());
          return true;
        }
      }
    }
    log.debug("isTargetIncludedOrExcludedInSegment -> segment list empty return false");
    return false;
  }

  protected boolean evaluateRule(ServingRule servingRule, Target target) {
    log.debug("run evaluateRule with rule {}", servingRule);
    return this.evaluateClauses(servingRule.getClauses(), target);
  }

  protected Optional<String> evaluateRules(List<ServingRule> servingRules, Target target) {
    log.debug("run evaluateRules with rule {}", servingRules);
    if (target == null || servingRules == null) {
      log.debug("evaluateRules -> target or serving rule is null");
      return Optional.empty();
    }

    log.debug("evaluateRules -> sorting serving rules {}", servingRules);
    servingRules.sort(Comparator.comparing(ServingRule::getPriority));
    log.debug("evaluateRules -> sorted serving rules {}", servingRules);
    for (ServingRule rule : servingRules) {
      // if evaluation is false just continue to next rule
      if (!this.evaluateRule(rule, target)) {
        log.debug(
            "evaluateRules -> unsuccessful evaluation of rule {} continue to next rule", rule);
        continue;
      }

      // rule matched, check if there is distribution
      Distribution distribution = rule.getServe().getDistribution();
      if (distribution != null) {
        log.debug("evaluateRules -> evaluate distribution {}", distribution);
        return evaluateDistribution(distribution, target);
      }

      // rule matched, here must be variation if distribution is undefined or null
      String identifier = rule.getServe().getVariation();
      if (identifier != null) {
        log.debug("evaluateRules -> return rule variation identifier {}", identifier);
        return Optional.of(identifier);
      }
    }
    log.debug("evaluateRules -> all rules failed, return empty identifier");
    return Optional.empty();
  }

  protected Optional<String> evaluateVariationMap(
      @NonNull List<VariationMap> variationMaps, Target target) {
    log.debug("run evaluateVariationMap with variationMap {}", variationMaps);
    if (target == null) {
      log.debug("evaluateVariationMap -> target is null");
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
        if (found.isPresent()) return Optional.of(variationMap.getVariation());
      }

      List<String> segmentIdentifiers = variationMap.getTargetSegments();
      if (segmentIdentifiers != null
          && isTargetIncludedOrExcludedInSegment(segmentIdentifiers, target)) {
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
    return Optional.empty();
  }

  protected boolean checkPreRequisite(FeatureConfig parentFeatureConfig, Target target) {
    List<Prerequisite> prerequisites = parentFeatureConfig.getPrerequisites();
    if (!CollectionUtils.isEmpty(prerequisites)) {
      log.info(
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
        log.info(
            "Pre requisite flag {} has variation {} for target {}",
            preReqFeatureConfig.get().getFeature(),
            preReqEvaluatedVariation.get(),
            target);

        // Compare if the pre requisite variation is a possible valid value of
        // the pre requisite FF
        List<String> validPreReqVariations = pqs.getVariations();
        log.info(
            "Pre requisite flag {} should have the variations {}",
            preReqFeatureConfig.get().getFeature(),
            validPreReqVariations);
        if (validPreReqVariations.stream()
            .noneMatch(element -> element.contains(preReqEvaluatedVariation.get().getValue()))) {
          return false;
        } else {
          return checkPreRequisite(preReqFeatureConfig.get(), target);
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
    MDC.put(targetKey, "no target");
    MDC.put("version", io.harness.cf.Version.VERSION);
    if (target != null) {
      MDC.put(targetKey, target.getIdentifier());
    }
    Optional<FeatureConfig> flag = query.getFlag(identifier);
    if (!flag.isPresent() || flag.get().getKind() != expected) {
      return Optional.empty();
    }

    try {
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
    return variation.map(value -> Boolean.parseBoolean(value.getValue())).orElse(defaultValue);
  }

  public String stringVariation(
      String identifier, Target target, String defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.STRING, callback);
    return variation.map(Variation::getValue).orElse(defaultValue);
  }

  public double numberVariation(
      String identifier, Target target, double defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.INT, callback);
    return variation.map(value -> Double.parseDouble(value.getValue())).orElse(defaultValue);
  }

  public JsonObject jsonVariation(
      String identifier, Target target, JsonObject defaultValue, FlagEvaluateCallback callback) {
    final Optional<Variation> variation =
        evaluate(identifier, target, FeatureConfig.KindEnum.JSON, callback);
    if (variation.isPresent())
      return new Gson().fromJson(variation.get().getValue(), JsonObject.class);
    return defaultValue;
  }
}
