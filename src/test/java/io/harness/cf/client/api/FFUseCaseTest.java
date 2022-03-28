package io.harness.cf.client.api;

import com.google.gson.JsonObject;
import io.harness.cf.client.dto.Target;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.ITest;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;

import java.lang.reflect.Method;

@Slf4j
public class FFUseCaseTest implements ITest {

  private final TestCase testCase;
  private final Evaluator evaluator;

  private ThreadLocal<String> testName = new ThreadLocal<>();

  public FFUseCaseTest(@NonNull final TestCase testCase, @NonNull final Evaluator evaluator) {
    this.testCase = testCase;
    this.evaluator = evaluator;
  }

  @BeforeMethod
  public void BeforeMethod(Method method, Object[] testData){
    testName.set(testCase.getTestName());
  }

  @Override
  public String getTestName() {
    return testName.get();
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
