package io.harness.cf.client.api.evaluator;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class EvaluatorTest {

    private final List<TestModel> testData;

    {


        testData = new LinkedList<>();
    }

    @Before
    public void prepareTestData() {

        try {

            final String testsLocation = "./src/test/ff-test-cases";
            final String testCasesPath = new File(testsLocation).getCanonicalPath();
            final File testCasesDirectory = new File(testCasesPath);

            Assert.assertTrue(testCasesDirectory.exists());

            log.info(String.format("Test cases directory: %s", testCasesPath));


            testData.clear();
            final Gson gson = new Gson();

        } catch (IOException e) {

            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testEvaluator() {

        // TODO: Process
    }
}
