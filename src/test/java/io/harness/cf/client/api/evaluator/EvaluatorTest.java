package io.harness.cf.client.api.evaluator;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Slf4j
public class EvaluatorTest {

    private final EvaluatorTesting tester;
    private final List<TestModel> testData;

    {

        testData = new LinkedList<>();
        tester = new EvaluatorTester();
    }

    @Test
    public void testEvaluator() {

        for (final TestModel model : testData) {

            tester.process(model);
        }
    }

    @Before
    public void prepareTestData() {

        try {

            final String testsLocation = "./src/test/ff-test-cases/tests";
            final String testCasesPath = new File(testsLocation).getCanonicalPath();
            final File testCasesDirectory = new File(testCasesPath);

            Assert.assertTrue(testCasesDirectory.exists());

            log.info(String.format("Test cases directory: %s", testCasesPath));

            final File[] files = testCasesDirectory.listFiles();

            Assert.assertNotNull(files);
            Assert.assertTrue(files.length > 0);

            testData.clear();

            final Gson gson = new Gson();

            for (final File file : files) {

                log.info(String.format("Processing the test file: %s", file.getName()));

                Assert.assertTrue(file.getName().toLowerCase(Locale.getDefault()).endsWith(".json"));

                final String json = read(file.getAbsolutePath());

                Assert.assertNotNull(json);
                Assert.assertFalse(json.trim().isEmpty());

                try {

                    final TestModel model = gson.fromJson(json, TestModel.class);

                    Assert.assertNotNull(model);

                    final String feature = model.flag.getFeature() + file.getName();
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
}
