package io.harness.cf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.harness.cf.client.common.Cache;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import io.harness.cf.model.Variation;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Slf4j
public class EvaluatorIntegrationTest {

  private final EvaluatorTester tester = new EvaluatorTester();
  private final List<TestModel> testData = new LinkedList<>();

  @Test
  public void testEvaluator() {

    for (final TestModel model : testData) {

      tester.process(model);
    }
  }

  @BeforeClass
  public void prepareTestData() {

    try {

      final String testsLocation = "./src/test/ff-test-cases/tests";
      final String testCasesPath = new File(testsLocation).getCanonicalPath();
      final File testCasesDirectory = new File(testCasesPath);

      Assert.assertTrue(testCasesDirectory.exists());

      log.info(String.format("Test cases directory: '%s'", testCasesPath));

      final File[] files = testCasesDirectory.listFiles();

      Assert.assertNotNull(files);
      Assert.assertTrue(files.length > 0);

      testData.clear();

      final Gson gson = new Gson();

      for (final File file : files) {

        log.info(String.format("Processing the test file: '%s'", file.getName()));
        Assert.assertTrue(file.getName().toLowerCase(Locale.getDefault()).endsWith(".json"));
        final String json = read(file.getAbsolutePath());

        Assert.assertNotNull(json);
        Assert.assertFalse(json.trim().isEmpty());

        try {
          final TestModel model = gson.fromJson(json, TestModel.class);
          Assert.assertNotNull(model);

          final String testFile = file.getName();
          final String feature = model.flag.getFeature() + testFile;

          model.testFile = testFile;
          model.flag.setFeature(feature);

          Assert.assertTrue(testData.add(model));

        } catch (JsonSyntaxException e) {
          Assert.fail(e.getMessage());
        }
      }
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }

  private static String read(final String path) {

    final StringBuilder builder = new StringBuilder();
    try (final Stream<String> stream = Files.lines(Paths.get(path), StandardCharsets.UTF_8)) {
      stream.forEach(s -> builder.append(s).append("\n"));
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
    return builder.toString();
  }

  private static class EvaluatorTester {

    private final String noTarget;
    private final Evaluation evaluator;
    private final Repository repository;
    private final List<TestResult> results;

    private final FlagEvaluateCallback flagEvaluateCallback =
        new FlagEvaluateCallback() {

          @Override
          public void processEvaluation(
              @NonNull FeatureConfig featureConfig, Target target, @NonNull Variation variation) {

            log.info(
                String.format(
                    "processEvaluation: '%s', '%s', '%s'",
                    featureConfig.getFeature(),
                    target != null ? target.getName() : noTarget,
                    variation.getValue()));
          }
        };

    {
      noTarget = "_no_target";

      final Cache cache = new CaffeineCache(10000);

      repository = new StorageRepository(cache, null);

      results = new LinkedList<>();
      evaluator = new Evaluator(repository);
    }

    public void process(final TestModel data) {

      log.info(String.format("Processing the test data '%s' started", data.testFile));

      repository.setFlag(data.flag.getFeature(), data.flag);

      final List<Segment> segments = data.segments;
      if (segments != null) {

        for (final Segment segment : segments) {

          repository.setSegment(segment.getIdentifier(), segment);
        }
      }

      for (final String key : data.expected.keySet()) {

        final boolean expected = data.expected.get(key);

        final TestResult result = new TestResult(data.testFile, key, expected, data);

        Assert.assertTrue(results.add(result));
      }

      for (final TestResult result : results) {

        log.info(
            String.format(
                "Use case '%s' with target '%s' and expected value '%b'",
                result.file, result.targetIdentifier, result.value));

        Target target = null;
        if (!noTarget.equals(result.targetIdentifier)) {
          if (result.useCase.targets != null) {
            for (final Target item : result.useCase.targets) {
              if (item != null && item.getIdentifier().equals(result.targetIdentifier)) {
                target = item;
                break;
              }
            }
          }
        }

        Object received = null;
        switch (result.useCase.flag.getKind()) {
          case BOOLEAN:
            received =
                evaluator.boolVariation(
                    result.useCase.flag.getFeature(), target, false, flagEvaluateCallback);
            break;

          case STRING:
            received =
                evaluator.stringVariation(
                    result.useCase.flag.getFeature(), target, "", flagEvaluateCallback);
            break;

          case INT:
            received =
                evaluator.numberVariation(
                    result.useCase.flag.getFeature(), target, 0, flagEvaluateCallback);
            break;

          case JSON:
            received =
                evaluator.jsonVariation(
                    result.useCase.flag.getFeature(),
                    target,
                    new JsonObject(),
                    flagEvaluateCallback);
            break;
        }
        Assert.assertEquals(result.value, received);
      }
      log.info(String.format("Processing the test data '%s' completed", data.testFile));
    }
  }

  private static class TestModel {

    public volatile String testFile;

    public FeatureConfig flag;
    public List<Target> targets;
    public List<Segment> segments;
    public HashMap<String, Boolean> expected;
  }

  private static class TestResult {

    final String file;
    final String targetIdentifier;
    final Boolean value;
    final TestModel useCase;

    public TestResult(
        final String file,
        final String targetIdentifier,
        final Boolean value,
        final TestModel useCase) {

      this.file = file;
      this.targetIdentifier = targetIdentifier;
      this.value = value;
      this.useCase = useCase;
    }
  }
}
