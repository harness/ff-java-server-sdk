package io.harness.cf.client.api;

import static org.testng.Assert.*;

import io.harness.cf.client.dto.Target;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.Listeners;

@Listeners(MockitoTestNGListener.class)
public class EvaluatorTest {
  private final String identifier = "identifier";

  private final Target target = Target.builder().identifier("harness").build();

  @Mock private StorageRepository query;

  @InjectMocks private Evaluator evaluator;

  @org.testng.annotations.Test
  public void testGetAttrValue() {

    Optional<Object> attrValue = evaluator.getAttrValue(target, identifier);

    assertTrue(attrValue.isPresent());
    assertEquals(attrValue.get(), "harness");
  }
}
