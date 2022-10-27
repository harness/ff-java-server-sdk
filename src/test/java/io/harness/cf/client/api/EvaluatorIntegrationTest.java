package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.harness.cf.client.common.Cache;
import io.harness.cf.model.Segment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

@Slf4j
public class EvaluatorIntegrationTest {
  private final Gson gson = new Gson();
  private final List<TestFileData> testData = new LinkedList<>();

  public void prepareTestData() {

    try {

      final String testsLocation = "./src/test/ff-test-cases/tests";
      final String testCasesPath = new File(testsLocation).getCanonicalPath();
      final File testCasesDirectory = new File(testCasesPath);

      assertTrue(testCasesDirectory.exists());

      log.info(String.format("Test cases directory: '%s'", testCasesPath));

      final File[] files = testCasesDirectory.listFiles();

      assertNotNull(files);
      assertTrue(files.length > 0);

      testData.clear();

      for (final File file : files) {

        log.info(String.format("Processing the test file: '%s'", file.getName()));
        assertTrue(file.getName().toLowerCase(Locale.getDefault()).endsWith(".json"));
        final String json = read(file.getAbsolutePath());

        assertNotNull(json);
        assertFalse(json.trim().isEmpty());

        try {
          final TestFileData fileData = gson.fromJson(json, TestFileData.class);
          assertNotNull(fileData);

          final String testFile = file.getName();
          final String feature = fileData.getFlag().getFeature() + testFile;

          fileData.setTestFile(testFile);
          fileData.getFlag().setFeature(feature);

          assertTrue(testData.add(fileData));

        } catch (JsonSyntaxException e) {
          fail(e.getMessage());
        }
      }
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  @TestFactory
  public List<DynamicTest> getTestCases() {
    prepareTestData();
    log.info("getTestCases invoked");
    final Cache cache = new CaffeineCache(10000);

    Repository repository = new StorageRepository(cache, null);
    Evaluator evaluator = new Evaluator(repository);

    List<DynamicTest> list = new ArrayList<>();

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
        final String testName = removeExtension(file.getTestFile());
        final TestCase testCase = new TestCase(file.getTestFile(), key, expected, file, testName);

        list.add(
            DynamicTest.dynamicTest(
                testName + "_with_target_" + testCase.getTargetIdentifier(),
                () -> new FFUseCaseTest(testCase, evaluator).runTestCase()));
      }
    }
    return list;
  }

  @NonNull
  private String read(@NonNull final String path) {

    final StringBuilder builder = new StringBuilder();
    try (final Stream<String> stream = Files.lines(Paths.get(path), StandardCharsets.UTF_8)) {
      stream.forEach(s -> builder.append(s).append("\n"));
    } catch (IOException e) {
      fail(e.getMessage());
    }
    return builder.toString();
  }

  public static String removeExtension(String fname) {
    int pos = fname.lastIndexOf('.');
    if (pos > -1) return fname.substring(0, pos);
    else return fname;
  }
}
