package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sangupta.murmur.Murmur3;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

@Slf4j
class Evaluator implements Evaluation {

  public static final int ONE_HUNDRED = 100;

  private final Query query;

  public Evaluator(Query query) {

    this.query = query;
  }

  protected Optional<Object> getAttrValue(Target target, @NonNull String attribute) {
    if (Strings.isNullOrEmpty(attribute)) {
      return Optional.empty();
    }
    try {
      Field field = Target.class.getDeclaredField(attribute);
      field.setAccessible(true);
      return Optional.of(field.get(target));
    } catch (NoSuchFieldException | IllegalAccessException e) {
      if (target.getAttributes() != null) {
        return Optional.of(target.getAttributes().get(attribute));
      }
    }
    log.error("The attribute {} does not exist", attribute);
    return Optional.empty();
  }

  protected Optional<Variation> findVariation(
      @NonNull List<Variation> variations, String identifier) {
    if (identifier == null || CollectionUtils.isEmpty(variations)) return Optional.empty();
    return variations.stream().filter(v -> v.getIdentifier().equals(identifier)).findFirst();
  }

  protected int getNormalizedNumber(@NonNull Object property, @NonNull String bucketBy) {
    byte[] value = String.join(":", bucketBy, property.toString()).getBytes();
    long hasher = Murmur3.hash_x86_32(value, value.length, Murmur3.UINT_MASK);
    return (int) (hasher % Evaluator.ONE_HUNDRED) + 1;
  }

  protected boolean isEnabled(Target target, String bucketBy, int percentage) {
    final Optional<Object> property = getAttrValue(target, bucketBy);
    if (!property.isPresent()) {
      return false;
    }
    int bucketId = getNormalizedNumber(property.get(), bucketBy);

    return percentage > 0 && bucketId <= percentage;
  }

  protected Optional<String> evaluateDistribution(Distribution distribution, Target target) {
    if (distribution == null) {
      return Optional.empty();
    }

    String variation = "";
    for (WeightedVariation weightedVariation : distribution.getVariations()) {
      variation = weightedVariation.getVariation();
      if (isEnabled(target, distribution.getBucketBy(), weightedVariation.getWeight())) {
        return Optional.of(weightedVariation.getVariation());
      }
    }
    return Optional.of(variation);
  }

  protected boolean evaluateClause(Clause clause, Target target) {
    if (clause == null) {
      return false;
    }
    // operator is required
    final String operator = clause.getOp();
    if (operator.isEmpty()) {
      return false;
    }

    Optional<Object> attrValue = getAttrValue(target, clause.getAttribute());
    if (!attrValue.isPresent()) {
      return false;
    }

    String object = attrValue.get().toString();
    String value = clause.getValues().get(0);

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
      case SEGMENT_MATCH:
        return isTargetIncludedOrExcludedInSegment(clause.getValues(), target);
      default:
        return false;
    }
  }

  protected boolean evaluateClauses(List<Clause> clauses, Target target) {
    for (Clause clause : clauses) {
      if (!evaluateClause(clause, target)) {
        return false;
      }
    }
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

    for (String segmentIdentifier : segmentList) {
      final Optional<Segment> optionalSegment = query.getSegment(segmentIdentifier);
      if (optionalSegment.isPresent()) {
        final Segment segment = optionalSegment.get();
        // Should Target be excluded - if in excluded list we return false
        if (isTargetInList(target, segment.getExcluded())) {
          log.debug(
              "Target {} excluded from segment {} via exclude list",
              target.getName(),
              segment.getName());
          return false;
        }

        // Should Target be included - if in included list we return true
        if (isTargetInList(target, segment.getIncluded())) {
          log.debug(
              "Target {} included in segment {} via include list",
              target.getName(),
              segment.getName());
          return true;
        }

        // Should Target be included via segment rules
        List<Clause> rules = segment.getRules();
        if ((rules != null) && !rules.isEmpty() && evaluateClauses(rules, target)) {
          log.debug(
              "Target {} included in segment {} via rules", target.getName(), segment.getName());
          return true;
        }
      }
    }
    return false;
  }

  protected boolean evaluateRule(ServingRule servingRule, Target target) {
    return this.evaluateClauses(servingRule.getClauses(), target);
  }

  protected Optional<String> evaluateRules(List<ServingRule> servingRules, Target target) {
    if (target == null || servingRules == null) {
      return Optional.empty();
    }

    servingRules.sort(Comparator.comparing(ServingRule::getPriority));
    for (ServingRule rule : servingRules) {
      // if evaluation is false just continue to next rule
      if (!this.evaluateRule(rule, target)) {
        continue;
      }

      // rule matched, check if there is distribution
      if (rule.getServe().getDistribution() != null) {
        return evaluateDistribution(rule.getServe().getDistribution(), target);
      }

      // rule matched, here must be variation if distribution is undefined or null
      if (rule.getServe().getVariation() != null) {
        return Optional.of(rule.getServe().getVariation());
      }
    }

    return Optional.empty();
  }

  protected Optional<String> evaluateVariationMap(
      @NonNull List<VariationMap> variationMaps, Target target) {
    if (target == null) {
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

    Optional<FeatureConfig> flag = query.getFlag(identifier);
    if (!flag.isPresent() || flag.get().getKind() != expected) return Optional.empty();

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
