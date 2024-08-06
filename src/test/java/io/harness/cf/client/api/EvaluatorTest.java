package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;
import static io.harness.cf.client.api.TestUtils.getJsonResource;
import static io.harness.cf.model.FeatureConfig.KindEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.harness.cf.JSON;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EvaluatorTest {

  private Evaluator evaluator;
  private List<FeatureConfig> features;

  @BeforeAll
  public void setupUp() throws IOException, URISyntaxException {
    final StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, false);
    evaluator = new Evaluator(repository, Mockito.mock(BaseConfig.class));

    loadSegments(repository, "local-test-cases/segments.json");

    final String featuresJson =
        getJsonResource("local-test-cases/percentage-rollout-with-zero-weights.json");
    features =
        new JSON().deserialize(featuresJson, new TypeToken<List<FeatureConfig>>() {}.getType());
    assertFalse(features.isEmpty());
  }

  enum ANDTest {
    // if (target.attr.email endswith '@harness.io' && target.attr.role = 'developer')

    EMAIL_IS_DEVELOPER("user@harness.io", "developer", true),
    EMAIL_IS_MANAGER("user@harness.io", "manager", false),
    EXTERNAL_EMAIL_IS_DEVELOPER("user@somewhereelse.com", "manager", false),
    EXTERNAL_EMAIL_IS_MANAGER("user@somewhereelse.com", "manager", false);

    final String attrEmail;
    final String attrRole;
    final boolean expected;

    ANDTest(String attrEmail, String attrRole, boolean expected) {
      this.attrEmail = attrEmail;
      this.attrRole = attrRole;
      this.expected = expected;
    }
  }

  @ParameterizedTest
  @EnumSource(ANDTest.class)
  public void testTargetV2AndOperator(ANDTest test) throws Exception {
    testTargetV2Operator(
        test.attrEmail, test.attrRole, "boolflag_and", String.valueOf(test.expected));
  }

  enum ORTest {
    // if (target.attr.email endswith '@harness.io' || target.attr.email endswith
    // '@somethingelse.com')

    EMAIL_ENDS_WITH_HARNESS("user@harness.io", true),
    EMAIL_ENDS_WITH_SOMETHING_ELSE("user@somethingelse.com", true),
    EMAIL_ENDS_WITH_GMAIL("user@gmail.com", false);

    final String email;
    final boolean expected;

    ORTest(String email, boolean expected) {
      this.email = email;
      this.expected = expected;
    }
  }

  @ParameterizedTest
  @EnumSource(ORTest.class)
  public void testTargetV2OrOperator(ORTest test) throws Exception {
    testTargetV2Operator(test.email, null, "boolflag_or", String.valueOf(test.expected));
  }

  private void testTargetV2Operator(String email, String role, String flagName, String expected)
      throws Exception {

    final StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, false);
    final Evaluator evaluator = new Evaluator(repository, Mockito.mock(BaseConfig.class));

    loadFlags(repository, "local-test-cases/v2-andor-flags.json");
    loadSegments(repository, "local-test-cases/v2-andor-segments.json");

    final Target target =
        Target.builder()
            .identifier("mytarget")
            .attributes(
                new HashMap<String, String>() {
                  {
                    put("email", String.valueOf(email));
                    put("role", String.valueOf(role));
                  }
                })
            .build();

    final Optional<Variation> result = evaluator.evaluate(flagName, target, BOOLEAN, null);

    assertTrue(result.isPresent());
    assertEquals(expected, result.get().getValue());
  }

  @Test
  public void checkBucket57IsMatchingCorrectly() {
    int bucket = Evaluator.getNormalizedNumber("test", "identifier");
    assertEquals(57, bucket);
  }

  @Test
  public void testPercentageRollout() throws URISyntaxException, IOException {

    // JSON has a weight distribution of 25%, 50%, 25%, 0%

    testPercentageRolloutMatches("nobody0@harness.io", "variationB"); // 36
    testPercentageRolloutMatches("nobody1@harness.io", "variationA"); // 10
    testPercentageRolloutMatches("nobody2@harness.io", "variationA"); // 17
    testPercentageRolloutMatches("nobody3@harness.io", "variationA"); // 15
    testPercentageRolloutMatches("nobody4@harness.io", "variationC"); // 86
  }

  private void testPercentageRolloutMatches(String targetIdentifier, String expectedVariant)
      throws URISyntaxException, IOException {

    final Target target =
        Target.builder().identifier(targetIdentifier).name(targetIdentifier).build();
    final Optional<String> actualVariant =
        evaluator.evaluateRules(features.get(0).getRules(), target);

    assertTrue(actualVariant.isPresent());
    assertEquals(expectedVariant, actualVariant.get());
  }

  @Test
  public void testPercentageRolloutDoesNotReturnZeroVariants()
      throws URISyntaxException, IOException {

    // JSON has a weight distribution of 25%, 50%, 25%, 0%
    // Create enough of a distribution spread to hit has many email hashes as we can

    final int RANDOM_EMAIL_COUNT = 1000;
    final Random random = new Random();

    for (int i = 0; i < RANDOM_EMAIL_COUNT; i++) {
      final String nextEmail = String.format("%d@harness.io", random.nextInt());

      final Target target = Target.builder().identifier(nextEmail).name(nextEmail).build();
      final Optional<String> actualVariant =
          evaluator.evaluateRules(features.get(0).getRules(), target);

      assertTrue(actualVariant.isPresent());
      assertNotEquals(
          "variationD", actualVariant.get()); // variantD is 0% and should never be returned
    }
  }

  @Test
  public void shouldReturnFalseWhenClauseEmptyOrInvalid() {
    final Target target =
        Target.builder().identifier("testuser@harness.io").name("testuser@harness.io").build();
    assertFalse(evaluator.evaluateClause(null, target));

    Clause mockClause = mock(Clause.class);

    when(mockClause.getOp()).thenReturn("");
    assertFalse(evaluator.evaluateClause(mockClause, target));

    when(mockClause.getOp()).thenReturn("notempty");
    when(mockClause.getValues()).thenReturn(Collections.emptyList());
    assertFalse(evaluator.evaluateClause(mockClause, target));

    when(mockClause.getOp()).thenReturn("notempty");
    when(mockClause.getValues()).thenReturn(Arrays.asList("@harness.io"));
    when(mockClause.getAttribute()).thenReturn("idontexist");
    assertFalse(evaluator.evaluateClause(mockClause, target));
  }

  @Test
  public void shouldEvaluateOperatorClauseCorrectly() {
    evaluateOperatorClause("testuser@harness.io", STARTS_WITH, "testuser");
    evaluateOperatorClause("testuser@harness.io", ENDS_WITH, ".io");
    evaluateOperatorClause("testuser@harness.io", MATCH, ".*harness.*");
    evaluateOperatorClause("testuser@harness.io", CONTAINS, "user");
    evaluateOperatorClause("testuser@harness.io", EQUAL, "testuser@Harness.IO");
    evaluateOperatorClause("testuser@harness.io", EQUAL_SENSITIVE, "testuser@harness.io");
    evaluateOperatorClause("abc", IN, "abc,def,harness,io");
  }

  @Test
  public void shouldReturnFalseIfOperatorNotKnown() {
    final Target target = Target.builder().identifier("dummy").name("dummy").build();

    Clause mockClause = mock(Clause.class);
    when(mockClause.getOp()).thenReturn("invalid-op");
    when(mockClause.getAttribute()).thenReturn("identifier");
    when(mockClause.getValues()).thenReturn(Arrays.asList("dummy"));
    assertFalse(evaluator.evaluateClause(mockClause, target));
  }

  private void evaluateOperatorClause(String operand, String op, String expected) {
    final Target target = Target.builder().identifier(operand).name(operand).build();

    List<String> expectedList =
        op.equals(IN) ? Arrays.asList(expected.split(",")) : Arrays.asList(expected);

    Clause mockClause = mock(Clause.class);
    when(mockClause.getOp()).thenReturn(op);
    when(mockClause.getAttribute()).thenReturn("identifier");
    when(mockClause.getValues()).thenReturn(expectedList);
    assertTrue(evaluator.evaluateClause(mockClause, target));

    when(mockClause.getAttribute()).thenReturn("name");
    when(mockClause.getValues()).thenReturn(expectedList);
    assertTrue(evaluator.evaluateClause(mockClause, target));
  }

  @Test
  public void shouldReturnFalseWhenGetAttrValueEmptyOrInvalid() {
    Target target =
        Target.builder().identifier("testuser@harness.io").name("testuser@harness.io").build();

    assertFalse(evaluator.getAttrValue(target, "").isPresent());
    assertFalse(evaluator.getAttrValue(null, "notempty").isPresent());
    assertFalse(evaluator.getAttrValue(target, "idontexist").isPresent());

    target = mock(Target.class);
    when(target.getAttributes()).thenReturn(null);
    assertFalse(evaluator.getAttrValue(target, "idontexist").isPresent());
  }

  @Test
  public void shouldReturnCorrectAttrForGetAttrValue() {
    Target target = Target.builder().identifier("testuser@harness.io").name("andrew").build();

    assertEquals("testuser@harness.io", evaluator.getAttrValue(target, "identifier").get());
    assertEquals("andrew", evaluator.getAttrValue(target, "name").get());
  }

  @Test
  public void shouldCorrectlyEvaluatePrereqsIfIdAndValueDiffer() throws Exception {
    final StorageRepository repo = new StorageRepository(new CaffeineCache(100), null, false);
    final Evaluator eval = new Evaluator(repo, Mockito.mock(BaseConfig.class));

    loadSegments(repo, "local-test-cases/segments.json");
    loadFlags(repo, "local-test-cases/pre-req-id-and-value-differ.json");

    final Target target = Target.builder().identifier("dummy_ident").name("dummy_name").build();

    // if the main flag doesn't return the expected value for each, we know the dependant flag is
    // not evaluating properly

    Optional<Variation> result;
    result = eval.evaluate("FeatureFlagWithDependency", target, BOOLEAN, null);
    assertTrue(result.isPresent());
    assertEquals("true", result.get().getValue());

    result = eval.evaluate("StringMultivariateFeatureFlagWithDependency", target, STRING, null);
    assertTrue(result.isPresent());
    assertEquals("value1", result.get().getValue());

    result = eval.evaluate("JsonMultivariateFeatureFlagWithDependency", target, JSON, null);
    assertTrue(result.isPresent());
    JsonObject json = (JsonObject) new JsonParser().parse(result.get().getValue());
    assertEquals("value1", json.get("test").getAsString());

    result = eval.evaluate("NumberMultivariateFeatureFlagWithDependency", target, INT, null);
    assertTrue(result.isPresent());
    assertEquals("1", result.get().getValue());
  }

  @Test
  public void testEvaluateRules() throws InterruptedException {

    final int threadCount = 10;
    final String test = "test";
    final Random random = new Random();
    final CountDownLatch latch = new CountDownLatch(threadCount);
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final CopyOnWriteArrayList<Exception> failures = new CopyOnWriteArrayList<>();
    final Query repository = new StorageRepository(new CaffeineCache(100), null, false);
    final Evaluator evaluator = new Evaluator(repository, Mockito.mock(BaseConfig.class));

    final List<String> values = new ArrayList<>();
    final List<Clause> clauses = new ArrayList<>();
    values.add(test);

    final Clause clause = Clause.builder().id(test).op(test).values(values).attribute(test).build();
    clauses.add(clause);

    final Serve serve = Serve.builder().variation(test).build();
    final Target target = Target.builder().identifier(test).name(test).build();

    final List<ServingRule> rules = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      final ServingRule rule =
          ServingRule.builder()
              .ruleId(test)
              .priority(random.nextInt())
              .clauses(clauses)
              .serve(serve)
              .build();
      rules.add(rule);
    }

    // Stress-test against the ConcurrentModificationException:
    for (int threadNo = 0; threadNo < threadCount; threadNo++) {
      executor.execute(
          () -> {
            try {
              for (int x = 0; x < 150; x++) {

                evaluator.evaluateRules(rules, target);
              }
            } catch (ConcurrentModificationException e) {
              failures.add(e);
            } finally {
              latch.countDown();
            }
          });
    }
    latch.await();
    for (final Exception e : failures) {
      fail("Failure", e);
    }
  }

  private void loadSegments(StorageRepository repository, String resourceName)
      throws IOException, URISyntaxException {
    String segmentsJson = getJsonResource(resourceName);
    List<Segment> segments =
        new JSON().deserialize(segmentsJson, new TypeToken<List<Segment>>() {}.getType());
    assertFalse(segments.isEmpty());
    for (Segment segment : segments) {
      repository.setSegment(segment.getIdentifier(), segment);
    }
  }

  private void loadFlags(StorageRepository repository, String resourceName)
      throws IOException, URISyntaxException {
    final String featuresJson = getJsonResource(resourceName);
    final List<FeatureConfig> featList =
        new JSON().deserialize(featuresJson, new TypeToken<List<FeatureConfig>>() {}.getType());
    assertFalse(featList.isEmpty());

    for (FeatureConfig config : featList) {
      repository.setFlag(config.getFeature(), config);
    }
  }
}
