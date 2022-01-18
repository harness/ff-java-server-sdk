package io.harness.cf.client.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.harness.cf.client.common.Cache;
import io.harness.cf.model.Segment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Factory;

@Slf4j
public class EvaluatorIntegrationTest {
  private final Gson gson = new Gson();
  private final List<TestFileData> testData = new LinkedList<>();

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

      for (final File file : files) {

        log.info(String.format("Processing the test file: '%s'", file.getName()));
        Assert.assertTrue(file.getName().toLowerCase(Locale.getDefault()).endsWith(".json"));
        final String json = read(file.getAbsolutePath());

        Assert.assertNotNull(json);
        Assert.assertFalse(json.trim().isEmpty());

        try {
          final TestFileData fileData = gson.fromJson(json, TestFileData.class);
          Assert.assertNotNull(fileData);

          final String testFile = file.getName();
          final String feature = fileData.getFlag().getFeature() + testFile;

          fileData.setTestFile(testFile);
          fileData.getFlag().setFeature(feature);

          Assert.assertTrue(testData.add(fileData));

        } catch (JsonSyntaxException e) {
          Assert.fail(e.getMessage());
        }
      }
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
  }

  @Factory
  public Object[] getTestCases() {
    prepareTestData();
    log.info("getTestCases invoked");
    final Cache cache = new CaffeineCache(10000);

    Repository repository = new StorageRepository(cache, null);
    Evaluator evaluator = new Evaluator(repository);

    List<Object> list = new ArrayList<>();

    for (TestFileData file : testData) {
      repository.setFlag(file.getFlag().getFeature(), file.getFlag());

      final List<Segment> segments = file.getSegments();
      if (segments != null) {
        for (final Segment segment : segments) {
          repository.setSegment(segment.getIdentifier(), segment);
        }
      }

      for (final String key : file.getExpected().keySet()) {
        final Object expected = file.getExpected().get(key);
        final TestCase testCase = new TestCase(file.getTestFile(), key, expected, file);
        final FFUseCaseTest useCase = new FFUseCaseTest(testCase, evaluator);
        Assert.assertTrue(list.add(useCase));
      }
    }
    return list.toArray();
  }

  @NonNull
  private String read(@NonNull final String path) {

    final StringBuilder builder = new StringBuilder();
    try (final Stream<String> stream = Files.lines(Paths.get(path), StandardCharsets.UTF_8)) {
      stream.forEach(s -> builder.append(s).append("\n"));
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
    return builder.toString();
  }
}
