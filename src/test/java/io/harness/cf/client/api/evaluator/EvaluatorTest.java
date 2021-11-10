package io.harness.cf.client.api.evaluator;

import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

public class EvaluatorTest {

    private final Gson gson;
    private final List<TestModel> testData;

    {

        gson = new Gson();
        testData = new LinkedList<>();
    }

    @Before
    public void prepareTestData() {

        // TODO: Populate with data
    }

    @Test
    public void testEvaluator() {

        // TODO: Process
    }
}
