package io.harness.cf.client.api.test.evaluator;

public class TestResult {

    final String file;
    final String targetIdentifier;
    final Boolean value;
    final TestModel useCase;

    public TestResult(

            final String file,
            final String targetIdentifier,
            final Boolean value,
            final TestModel useCase
    ) {

        this.file = file;
        this.targetIdentifier = targetIdentifier;
        this.value = value;
        this.useCase = useCase;
    }
}
