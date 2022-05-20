package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.ServingRule;
import java.util.LinkedList;
import java.util.List;
import junit.framework.TestCase;

public class EvaluatorTest extends TestCase {

  public void testEvaluateRules() {

    final Query repository = new StorageRepository(new CaffeineCache(100), null, null);
    final Evaluator evaluator = new Evaluator(repository);

    final String test = "test";
    final ServingRule rule = ServingRule.builder().ruleId(test).build();
    final Target target = Target.builder().identifier(test).name(test).build();

    final List<ServingRule> rules = new LinkedList<>();
    rules.add(rule);

    evaluator.evaluateRules(rules, target);
  }
}
