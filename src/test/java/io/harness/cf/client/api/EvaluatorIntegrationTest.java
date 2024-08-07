package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

@Slf4j
public class EvaluatorIntegrationTest {
  private final Gson gson = new Gson();

  @DisplayName("ff-test-cases")
  @TestFactory
  public List<DynamicTest> getTestCases() throws Exception {
    final List<DynamicTest> list = new ArrayList<>();
    final String testCasesBasePath = new File("./src/test/ff-test-cases/tests").getCanonicalPath();
    final File testCasesDirectory = new File(testCasesBasePath);

    assertTrue(
        testCasesDirectory.exists(),
        "ff-test-cases folder missing - please check 'git submodule init' has been run");

    try (Stream<Path> pathStream = Files.walk(Paths.get(testCasesBasePath))) {
      pathStream
          .filter(Files::isRegularFile)
          .forEach(
              path -> {
                File file = path.toAbsolutePath().toFile();
                String canonicalPath;
                try {
                  canonicalPath = file.getCanonicalPath();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }

                if (!file.getName().toLowerCase().endsWith(".json")) {
                  log.warn("Skipping {}", file.getAbsolutePath());
                  return;
                }
                log.info("Loading {}", file.getAbsolutePath());

                final String json = read(file.getAbsolutePath());
                final TestFileData fileData = gson.fromJson(json, TestFileData.class);
                assertNotNull(fileData);

                if (fileData.getTests() == null) {
                  log.warn("skipping file, no tests found " + file.getName());
                  return;
                }

                for (Map<String, Object> nextTest : fileData.getTests()) {
                  final Object expected = nextTest.get("expected");
                  final String target = (String) nextTest.get("target"); // May be null
                  final String flag = (String) nextTest.get("flag");
                  final FeatureConfig feature = findFeatureConfig(flag, fileData.getFlags());

                  final TestCase testCase =
                      new TestCase(
                          file.getName(),
                          target,
                          expected,
                          flag,
                          feature.getKind(),
                          fileData,
                          removeExtension(file.getName()));

                  final Repository repository =
                      new StorageRepository(new CaffeineCache(10000), null, false);
                  final Evaluator evaluator =
                      new Evaluator(repository, Mockito.mock(BaseConfig.class));

                  loadSegments(repository, fileData.getSegments());
                  loadFlags(repository, fileData.getFlags());

                  String junitTestName =
                      removeExtension(canonicalPath).replace(testCasesBasePath + "/", "");
                  junitTestName += "__with_flag_" + testCase.getFlag();
                  if (testCase.getTargetIdentifier() != null) {
                    junitTestName += "__with_target_" + testCase.getTargetIdentifier();
                  }

                  list.add(
                      DynamicTest.dynamicTest(
                          junitTestName,
                          () -> new FFUseCaseTest(testCase, evaluator).runTestCase()));
                }
              });
    }

    return list;
  }

  private FeatureConfig findFeatureConfig(String flagName, List<FeatureConfig> flags) {
    for (FeatureConfig nextFlag : flags) {
      if (nextFlag.getFeature().equals(flagName)) {
        return nextFlag;
      }
    }
    throw new IllegalArgumentException("Unknown flag name " + flagName);
  }

  private void loadFlags(Repository repository, List<FeatureConfig> flags) {
    if (flags != null) {
      for (FeatureConfig nextFlag : flags) {
        repository.setFlag(nextFlag.getFeature(), nextFlag);
      }
    }
  }

  private void loadSegments(Repository repository, List<Segment> segments) {
    if (segments != null) {
      for (final Segment segment : segments) {
        repository.setSegment(segment.getIdentifier(), segment);
      }
    }
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
