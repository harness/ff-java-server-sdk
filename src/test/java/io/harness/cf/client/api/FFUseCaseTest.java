package io.harness.cf.client.api;

import com.google.gson.JsonObject;
import io.harness.cf.client.dto.Target;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class FFUseCaseTest {

  private final TestCase testCase;
  private final Evaluator evaluator;

  public FFUseCaseTest(@NonNull final TestCase testCase, @NonNull final Evaluator evaluator) {
    this.testCase = testCase;
    this.evaluator = evaluator;
  }

  @Test
  public void runTestCase() {
    log.info(
        String.format(
            "Use case '%s' with target '%s' and expected value '%b'",
            testCase.getFile(), testCase.getTargetIdentifier(), testCase.getExpectedValue()));
    String noTarget = "_no_target";
    Target target = null;
    if (!noTarget.equals(testCase.getTargetIdentifier())) {
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
    switch (testCase.getFileData().getFlag().getKind()) {
      case BOOLEAN:
        got =
            evaluator.boolVariation(
                testCase.getFileData().getFlag().getFeature(), target, false, null);
        break;

      case STRING:
        got =
            evaluator.stringVariation(
                testCase.getFileData().getFlag().getFeature(), target, "", null);
        break;

      case INT:
        got =
            evaluator.numberVariation(
                testCase.getFileData().getFlag().getFeature(), target, 0, null);
        break;

      case JSON:
        got =
            evaluator.jsonVariation(
                testCase.getFileData().getFlag().getFeature(), target, new JsonObject(), null);
        break;
    }
    String msg =
        String.format(
            "Test case: %s with identifier %s ",
            testCase.getFile(), testCase.getTargetIdentifier());
    Assert.assertEquals(testCase.getExpectedValue(), got, msg);
  }
}
