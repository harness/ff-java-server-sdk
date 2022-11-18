package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import io.harness.cf.client.dto.Target;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FFUseCaseTest {

  private final TestCase testCase;
  private final Evaluator evaluator;

  public FFUseCaseTest(@NonNull final TestCase testCase, @NonNull final Evaluator evaluator) {
    this.testCase = testCase;
    this.evaluator = evaluator;
  }

  public void runTestCase() {
    log.info(
        String.format(
            "Use case '%s' with target '%s' and flag '%s' is expected to be '%b'",
            testCase.getFile(),
            testCase.getTargetIdentifier(),
            testCase.getFlag(),
            testCase.getExpectedValue()));

    Target target = null;
    if (!"_no_target".equals(testCase.getTargetIdentifier())) {
      if (testCase.getFileData().getTargets() != null) {
        for (final Target item : testCase.getFileData().getTargets()) {
          if (item != null && item.getIdentifier().equals(testCase.getTargetIdentifier())) {
            target = item;
            break;
          }
        }
      }
    }

    Object got = null;
    switch (testCase.getFlagKind()) {
      case BOOLEAN:
        got = evaluator.boolVariation(testCase.getFlag(), target, false, null);
        break;

      case STRING:
        got = evaluator.stringVariation(testCase.getFlag(), target, "", null);
        break;

      case INT:
        got = evaluator.numberVariation(testCase.getFlag(), target, 0, null);
        break;

      case JSON:
        got = evaluator.jsonVariation(testCase.getFlag(), target, new JsonObject(), null);
        break;
    }

    log.info("FLAG    : " + testCase.getFlag());
    log.info("TARGET  : " + (target == null ? "(none)" : target.getIdentifier()));
    log.info("EXPECTED: " + testCase.getExpectedValue());
    log.info("GOT     : " + got);

    String msg =
        String.format(
            "Test case: %s with identifier %s ",
            testCase.getFile(), testCase.getTargetIdentifier());

    assertEquals(testCase.getExpectedValue(), got, msg);
  }
}
