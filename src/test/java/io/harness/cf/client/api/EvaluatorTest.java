package io.harness.cf.client.api;

import static io.harness.cf.client.api.Operators.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.reflect.TypeToken;
import io.harness.cf.JSON;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EvaluatorTest {

  private Evaluator evaluator;
  private List<FeatureConfig> features;

  @BeforeAll
  public void setupUp() throws IOException, URISyntaxException {
    final StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, null);
    evaluator = new Evaluator(repository);

    loadSegments(repository);

    final String featuresJson =
        getJsonResource("local-test-cases/percentage-rollout-with-zero-weights.json");
    features =
        new JSON().deserialize(featuresJson, new TypeToken<List<FeatureConfig>>() {}.getType());
    assertFalse(features.isEmpty());
  }

  @Test
  public void testPercentageRollout() throws URISyntaxException, IOException {

    // JSON has a weight distribution of 25%, 50%, 25%, 0%

    testPercentageRolloutMatches("andrew.bell@harness.io", "variationB"); // 31
    testPercentageRolloutMatches("nobody1@harness.io", "variationA"); // 19
    testPercentageRolloutMatches("nobody2@harness.io", "variationB"); // 36
    testPercentageRolloutMatches("nobody3@harness.io", "variationA"); // 12
    testPercentageRolloutMatches("nobody4@harness.io", "variationC"); // 87
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
        Target.builder()
            .identifier("andrew.bell@harness.io")
            .name("andrew.bell@harness.io")
            .build();
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
    evaluateOperatorClause("andrew.bell@harness.io", STARTS_WITH, "andrew");
    evaluateOperatorClause("andrew.bell@harness.io", ENDS_WITH, ".io");
    evaluateOperatorClause("andrew.bell@harness.io", MATCH, ".*harness.*");
    evaluateOperatorClause("andrew.bell@harness.io", CONTAINS, "bell");
    evaluateOperatorClause("andrew.bell@harness.io", EQUAL, "Andrew.Bell@Harness.IO");
    evaluateOperatorClause("andrew.bell@harness.io", EQUAL_SENSITIVE, "andrew.bell@harness.io");
    evaluateOperatorClause("andrew", IN, "andrew,bell,harness,io");
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
        Target.builder()
            .identifier("andrew.bell@harness.io")
            .name("andrew.bell@harness.io")
            .build();

    assertFalse(evaluator.getAttrValue(target, "").isPresent());
    assertFalse(evaluator.getAttrValue(null, "notempty").isPresent());
    assertFalse(evaluator.getAttrValue(target, "idontexist").isPresent());

    target = mock(Target.class);
    when(target.getAttributes()).thenReturn(null);
    assertFalse(evaluator.getAttrValue(target, "idontexist").isPresent());
  }

  @Test
  public void shouldReturnCorrectAttrForGetAttrValue() {
    Target target = Target.builder().identifier("andrew.bell@harness.io").name("andrew").build();

    assertEquals("andrew.bell@harness.io", evaluator.getAttrValue(target, "identifier").get());
    assertEquals("andrew", evaluator.getAttrValue(target, "name").get());
  }

  @Test
  public void testEvaluateRules() throws InterruptedException {

    final int threadCount = 10;
    final String test = "test";
    final Random random = new Random();
    final CountDownLatch latch = new CountDownLatch(threadCount);
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final CopyOnWriteArrayList<Exception> failures = new CopyOnWriteArrayList<>();
    final Query repository = new StorageRepository(new CaffeineCache(100), null, null);
    final Evaluator evaluator = new Evaluator(repository);

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

  private String getJsonResource(String location) throws IOException, URISyntaxException {
    Path path = Paths.get(EvaluatorTest.class.getClassLoader().getResource(location).toURI());
    return new String(Files.readAllBytes(path));
  }

  private void loadSegments(StorageRepository repository) throws IOException, URISyntaxException {
    String segmentsJson = getJsonResource("local-test-cases/segments.json");
    List<Segment> segments =
        new JSON().deserialize(segmentsJson, new TypeToken<List<Segment>>() {}.getType());
    assertFalse(segments.isEmpty());
    for (Segment segment : segments) {
      repository.setSegment(segment.getIdentifier(), segment);
    }
  }
}
