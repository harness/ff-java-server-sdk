package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.Clause;
import io.harness.cf.model.Serve;
import io.harness.cf.model.ServingRule;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class EvaluatorTest {

  @Test
  public void testEvaluateRules() {
    int threadCount = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    final Executor executor = Executors.newFixedThreadPool(threadCount);
    final Query repository = new StorageRepository(new CaffeineCache(100), null, null);
    final Evaluator evaluator = new Evaluator(repository);

    final String test = "test";

    final List<String> values = new LinkedList<>();
    final List<Clause> clauses = new LinkedList<>();
    values.add(test);

    final Clause clause = Clause.builder().id(test).op(test).values(values).attribute(test).build();
    clauses.add(clause);

    final Serve serve = Serve.builder().variation(test).build();
    final Target target = Target.builder().identifier(test).name(test).build();

    final List<ServingRule> rules = new LinkedList<>();
    for (int i = 0; i < 1000; i++) {
      final ServingRule rule =
          ServingRule.builder()
              .ruleId(test)
              .priority(new Random().nextInt())
              .clauses(clauses)
              .serve(serve)
              .build();
      rules.add(rule);
    }

    // Stress-test against the ConcurrentModificationException:
    for (int threadNo = 0; threadNo < threadCount; threadNo++) {

      executor.execute(
          () -> {
            for (int x = 0; x < 1000; x++) {

              evaluator.evaluateRules(rules, target);
            }
            latch.countDown();
          });
    }

    try {
      latch.await(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      Assert.fail();
    }
  }
}
