package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.Clause;
import io.harness.cf.model.Serve;
import io.harness.cf.model.ServingRule;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.Test;

@Slf4j
public class EvaluatorTest {

  @Test
  public void testEvaluateRules() {

    final Executor executor = Executors.newFixedThreadPool(100);
    final Query repository = new StorageRepository(new CaffeineCache(100), null, null);
    final Evaluator evaluator = new Evaluator(repository);

    final String test = "test";

    final List<String> values = new LinkedList<>();
    final List<Clause> clauses = new LinkedList<>();
    values.add(test);

    final Clause clause = Clause.builder().id(test).op(test).values(values).attribute(test).build();
    clauses.add(clause);

    final Serve serve = Serve.builder().variation(test).build();
    final ServingRule rule =
        ServingRule.builder().ruleId(test).priority(0).clauses(clauses).serve(serve).build();
    final Target target = Target.builder().identifier(test).name(test).build();

    final List<ServingRule> rules = new LinkedList<>();
    rules.add(rule);

    for (int threadNo = 0; threadNo < 10; threadNo++) {

      executor.execute(
          () -> {
            for (int x = 0; x < 1000; x++) {

              evaluator.evaluateRules(rules, target);
            }
          });
    }
  }
}
