package io.harness.cf.client.api;

import static org.junit.Assert.*;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.harness.cf.client.dto.*;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;

/** EvaluatorTest tests the flag configuration rules are correctly evaluated */
public class EvaluatorTest {

  private static Evaluator evaluator;
  private static Cache<String, Segment> cache;

  private static Target target1 =
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

  private static Target target2 =
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

  /**
   * Test that the flag serves the correct default variations when the flag is turned off/on
   *
   * @throws CfClientException
   */
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
   *
   * @throws CfClientException
   */
  @Test
  public void testTarget() throws CfClientException {
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addTargetMappingRule(fc, target1.getIdentifier(), "false");

    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());
  }

  /**
   * Test that segment mapping overrides the default serve It should serve the variation specified
   * by the segment mapping, if the target belongs to the segment
   *
   * @throws CfClientException
   */
  @Test
  public void testSegmentIncludeList() throws CfClientException {
    // Create Segment and add it to the evaluator cache
    Segment segment =
        SegmentBuilder.aTargetSegment()
            .withIdentifier("Alpha")
            .withName("alpha")
            .withIncluded(Arrays.asList(target1.getIdentifier()))
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier(), "false");

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
   *
   * @throws CfClientException
   */
  @Test
  public void testSegmentExcludeList() throws CfClientException {
    // Create Include List
    List<String> included = Arrays.asList(target1.getIdentifier(), target2.getIdentifier());

    // Create Exclude List - exclude segmentMember1
    List<String> excluded = Arrays.asList(target1.getIdentifier());

    // Create Segment and add it to the evaluator cache
    Segment segment =
        SegmentBuilder.aTargetSegment()
            .withIdentifier("Alpha")
            .withName("alpha")
            .withIncluded(included)
            .withExcluded(excluded)
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier(), "false");

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
   *
   * @throws CfClientException
   */
  @Test
  public void testSegmentRules() throws CfClientException {
    List<Clause> clauses =
        Arrays.asList(
            ClauseBuilder.aClause()
                .withAttribute("identifier")
                .withOp(Operators.EQUAL)
                .withValue(Arrays.asList(target1.getIdentifier()))
                .build(),
            ClauseBuilder.aClause()
                .withAttribute("name")
                .withOp(Operators.EQUAL)
                .withValue(Arrays.asList(target2.getName()))
                .build());

    // Create Segment and add it to the evaluator cache
    Segment segment =
        SegmentBuilder.aTargetSegment()
            .withIdentifier("Alpha")
            .withName("alpha")
            .withRules(clauses)
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier(), "false");

    // Serve false to segment member
    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());

    // Serve default serve (true) to non segment member
    result = evaluator.evaluate(fc, target2);
    assertEquals("false", result.getValue());

    removeSegmentFromCache(segment);
  }

  /**
   * Test that boolean values are correctly evaluated in segment rules
   *
   * @throws CfClientException
   */
  @Test
  public void testSegmentBooleanRules() throws CfClientException {
    List<Clause> clauses =
        Arrays.asList(
            ClauseBuilder.aClause()
                .withAttribute("happy")
                .withOp(Operators.EQUAL)
                .withValue(Arrays.asList(new Boolean(true).toString()))
                .build());

    // Create Segment and add it to the evaluator cache
    Segment segment =
        SegmentBuilder.aTargetSegment()
            .withIdentifier("Alpha")
            .withName("alpha")
            .withRules(clauses)
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier(), "false");

    // Serve false to segment member (included by happy)
    Variation result = evaluator.evaluate(fc, target1);
    assertEquals("false", result.getValue());

    // Serve true to non segment member
    result = evaluator.evaluate(fc, target2);
    assertEquals("true", result.getValue());

    removeSegmentFromCache(segment);
  }

  /**
   * Test that boolean values are correctly evaluated in segment rules
   *
   * @throws CfClientException
   */
  @Test
  public void testSegmentIntegerRules() throws CfClientException {
    List<Clause> clauses =
        Arrays.asList(
            ClauseBuilder.aClause()
                .withAttribute("age")
                .withOp(Operators.EQUAL)
                .withValue(Arrays.asList(new Integer(22).toString()))
                .build());

    // Create Segment and add it to the evaluator cache
    Segment segment =
        SegmentBuilder.aTargetSegment()
            .withIdentifier("Alpha")
            .withName("alpha")
            .withRules(clauses)
            .build();

    addSegmentToCache(segment);

    // Create the flag configuration and add the segment
    FeatureConfig fc = booleanFlag(FeatureState.ON);
    addSegmentMappingRule(fc, segment.getIdentifier(), "false");

    // Serve false to segment member (included by age 22)
    Variation result = evaluator.evaluate(fc, target2);
    assertEquals("false", result.getValue());

    // Serve true to non segment member
    result = evaluator.evaluate(fc, target1);
    assertEquals("true", result.getValue());

    removeSegmentFromCache(segment);
  }

  /** * The functions below are helpers for running the tests */

  /**
   * Add a Target Mapping rule to the flag configuration. The target should be served the specified
   * variation
   *
   * @param featureConfig
   * @param targetID
   * @param targetVariation
   * @return
   */
  private static FeatureConfig addTargetMappingRule(
      FeatureConfig featureConfig, String targetID, String targetVariation) {
    featureConfig.addVariationToTargetMapItem(
        VariationMapBuilder.aVariationMap()
            .variation(targetVariation)
            .targets(Arrays.asList(targetID))
            .build());
    return featureConfig;
  }

  /**
   * Add a Segment Mapping rule to the flag configuration. Any targets that belong to this segment
   * should receive the specified variation
   *
   * @param featureConfig
   * @param segmentID
   * @param segmentVariation
   * @return
   */
  private static FeatureConfig addSegmentMappingRule(
      FeatureConfig featureConfig, String segmentID, String segmentVariation) {
    featureConfig.addVariationToTargetMapItem(
        VariationMapBuilder.aVariationMap()
            .variation(segmentVariation)
            .targetSegments(Arrays.asList(segmentID))
            .build());

    return featureConfig;
  }

  /**
   * Creates a boolean flag Config, with two variations (true|false). By default if the flag is on,
   * it will serve true, and if its off it will serve false.
   *
   * @param state
   * @return
   */
  private static FeatureConfig booleanFlag(FeatureState state) {
    return FeatureConfigBuilder.aFeatureConfig()
        .state(state)
        .offVariation("false")
        .variations(
            ImmutableList.<Variation>builder()
                .add(
                    VariationBuilder.aVariation()
                        .identifier("false")
                        .name("false")
                        .value("false")
                        .build())
                .add(
                    VariationBuilder.aVariation()
                        .identifier("true")
                        .name("true")
                        .value("true")
                        .build())
                .build())
        .defaultServe(ServeBuilder.aServe().variation("true").build())
        .build();
  }

  /**
   * Adds segment to the cache
   *
   * @param segment
   */
  public static void addSegmentToCache(Segment segment) {
    cache.put(segment.getIdentifier(), segment);
  }

  public static void removeSegmentFromCache(Segment segment) {
    cache.invalidate(segment.getIdentifier());
  }
}
