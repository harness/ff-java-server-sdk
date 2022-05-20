package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.Clause;
import io.harness.cf.model.Serve;
import io.harness.cf.model.ServingRule;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.Test;

@Slf4j
public class EvaluatorTest {

  @Test
  public void testEvaluateRules() throws InterruptedException {

    final int threadCount = 10;
    final String test = "test";
    final AtomicBoolean failed = new AtomicBoolean();
    final CountDownLatch latch = new CountDownLatch(threadCount);
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final Query repository = new StorageRepository(new CaffeineCache(100), null, null);
    final Evaluator evaluator = new Evaluator(repository);

    final List<String> values = new ArrayList<>();
    final List<Clause> clauses = new ArrayList<>();
    values.add(test);

    final Clause clause = Clause.builder().id(test).op(test).values(values).attribute(test).build();
    clauses.add(clause);

    final Serve serve = Serve.builder().variation(test).build();
    final Target target = Target.builder().identifier(test).name(test).build();

    final List<ServingRule> rules = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
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
            try {
              for (int x = 0; x < 150; x++) {

                evaluator.evaluateRules(rules, target);
              }
            } catch (ConcurrentModificationException e) {
              e.printStackTrace();
              failed.set(true);
            } finally {
              latch.countDown();
            }
          });
    }
    latch.await();
    Assert.assertFalse(failed.get());
  }
}
