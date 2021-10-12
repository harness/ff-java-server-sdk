package io.harness.cf.client.api;

import static org.junit.Assert.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;

/** EvaluatorTest tests the flag configuration rules are correctly evaluated */
public class EvaluatorTest {

  private static Evaluator evaluator;
  private static Cache<String, Segment> cache;

  private static final Target target1 =
      Target.builder()
          .identifier("bob")
          .name("Bob")
          .attributes(
              new ImmutableMap.Builder<String, Object>()
                  .put("company", "harness")
                  .put("age", 56)
                  .put("happy", true)
                  .build())
          .build();

  private static final Target target2 =
      Target.builder()
          .identifier("joe")
          .name("Joe")
          .attributes(
              new ImmutableMap.Builder<String, Object>()
                  .put("company", "ACME")
                  .put("age", 22)
                  .put("happy", false)
                  .build())
          .build();

  @BeforeClass
  public static void beforeClass() {
    cache = Caffeine.newBuilder().maximumSize(10000).build();
    evaluator = new Evaluator(cache);
  }

  /** Test that the flag serves the correct default variations when the flag is turned off/on */
  @Test
  public void testBooleanFlagStates() throws CfClientException {
    Variation result = evaluator.evaluate(booleanFlag(FeatureState.ON), target1);
    assertEquals("true", result.getValue());

    result = evaluator.evaluate(booleanFlag(FeatureState.OFF), target1);
    assertEquals("false", result.getValue());
  }

  /**
   * Test that target mapping overrides the default serve It should serve the variation specified by
   * the target mapping
   */
  @Test
  public void testTarget() throws CfClientException {
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addTargetMappingRule(fc, target1.getIdentifier());

    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());
  }

  /**
   * Test that segment mapping overrides the default serve It should serve the variation specified
   * by the segment mapping, if the target belongs to the segment
   */
  @Test
  public void testSegmentIncludeList() throws CfClientException {
    // Create Segment and add it to the evaluator cache
    Segment segment =
        Segment.builder()
            .identifier("Alpha")
            .name("alpha")
            .included(Collections.singletonList(target1.ApiTarget()))
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier());

    // Serve false to segment member
    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());

    // Serve default serve (true) to non segment member
    result = evaluator.evaluate(fc, target2);
    assertEquals("true", result.getValue());

    removeSegmentFromCache(segment);
  }

  /**
   * Test that segment mapping overrides the default serve It should serve the variation specified
   * by the segment mapping, unless the target is excluded by the to the segment. The exclude list
   * should take precedence
   */
  @Test
  public void testSegmentExcludeList() throws CfClientException {
    // Create Include List
    List<io.harness.cf.model.Target> included =
        Arrays.asList(target1.ApiTarget(), target2.ApiTarget());

    // Create Exclude List - exclude segmentMember1
    List<io.harness.cf.model.Target> excluded = Collections.singletonList(target1.ApiTarget());

    // Create Segment and add it to the evaluator cache
    Segment segment =
        Segment.builder()
            .identifier("Alpha")
            .name("alpha")
            .included(included)
            .excluded(excluded)
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier());

    // Serve false to segment member
    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("true", result.getValue());

    // Serve default serve (true) to non segment member
    result = evaluator.evaluate(fc, target2);
    assertEquals("false", result.getValue());

    removeSegmentFromCache(segment);
  }

  /**
   * Test that segment mapping overrides the default serve It should serve the variation specified
   * by the segment mapping, unless the target is excluded by the to the segment
   */
  @Test
  public void testSegmentRules() throws CfClientException {
    List<Clause> clauses =
        Arrays.asList(
            Clause.builder()
                .attribute("identifier")
                .op(Operators.EQUAL)
                .values(Collections.singletonList(target1.getIdentifier()))
                .build(),
            Clause.builder()
                .attribute("name")
                .op(Operators.EQUAL)
                .values(Collections.singletonList(target2.getName()))
                .build());

    // Create Segment and add it to the evaluator cache
    Segment segment = Segment.builder().identifier("Alpha").name("alpha").rules(clauses).build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier());

    // Serve false to segment member
    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());

    // Serve default serve (true) to non segment member
    result = evaluator.evaluate(fc, target2);
    assertEquals("false", result.getValue());

    removeSegmentFromCache(segment);
  }

  /** Test that boolean values are correctly evaluated in segment rules */
  @Test
  public void testSegmentBooleanRules() throws CfClientException {
    List<Clause> clauses =
        Collections.singletonList(
            Clause.builder()
                .attribute("happy")
                .op(Operators.EQUAL)
                .values(Collections.singletonList(Boolean.toString(true)))
                .build());

    // Create Segment and add it to the evaluator cache
    Segment segment = Segment.builder().identifier("Alpha").name("alpha").rules(clauses).build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier());

    // Serve false to segment member (included by happy)
    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());

    // Serve true to non segment member
    result = evaluator.evaluate(fc, target2);
    assertEquals("true", result.getValue());

    removeSegmentFromCache(segment);
  }

  /** Test that boolean values are correctly evaluated in segment rules */
  @Test
  public void testSegmentIntegerRules() throws CfClientException {
    List<Clause> clauses =
        Collections.singletonList(
            Clause.builder()
                .attribute("age")
                .op(Operators.EQUAL)
                .values(Collections.singletonList(Integer.toString(22)))
                .build());

    // Create Segment and add it to the evaluator cache
    Segment segment = Segment.builder().identifier("Alpha").name("alpha").rules(clauses).build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier());

    // Serve false to segment member (included by age 22)
    Variation result = evaluator.evaluate(fc, target2);
    assertEquals("false", result.getValue());

    // Serve true to non segment member
    result = evaluator.evaluate(fc, target1);
    assertEquals("true", result.getValue());

    removeSegmentFromCache(segment);
  }

  // The functions below are helpers for running the tests

  /**
   * Add a Target Mapping rule to the flag configuration. The target should be served the specified
   * variation
   */
  private static void addTargetMappingRule(FeatureConfig featureConfig, String targetID) {
    featureConfig.addVariationToTargetMapItem(
        VariationMap.builder()
            .variation("false")
            .targets(Collections.singletonList(TargetMap.builder().identifier(targetID).build()))
            .build());
  }

  /**
   * Add a Segment Mapping rule to the flag configuration. Any targets that belong to this segment
   * should receive the specified variation
   */
  private static void addSegmentMappingRule(FeatureConfig featureConfig, String segmentID) {

    featureConfig.addVariationToTargetMapItem(
        VariationMap.builder()
            .variation("false")
            .targetSegments(Collections.singletonList(segmentID))
            .build());
  }

  /**
   * Creates a boolean flag Config, with two variations (true|false). By default if the flag is on,
   * it will serve true, and if its off it will serve false.
   */
  private static FeatureConfig booleanFlag(FeatureState state) {

    return FeatureConfig.builder()
        .state(state)
        .offVariation("false")
        .variations(
            ImmutableList.<Variation>builder()
                .add(Variation.builder().identifier("false").name("false").value("false").build())
                .add(Variation.builder().identifier("true").name("true").value("true").build())
                .build())
        .defaultServe(Serve.builder().variation("true").build())
        .build();
  }

  public static void addSegmentToCache(Segment segment) {

    cache.put(segment.getIdentifier(), segment);
  }

  public static void removeSegmentFromCache(Segment segment) {

    cache.invalidate(segment.getIdentifier());
  }
}
