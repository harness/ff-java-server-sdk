package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.harness.cf.client.api.testutils.DummyConnector;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/*
 * This stress test is disabled by default and needs to be run manually. Make sure to set TARGETS_FILE and
 * FLAGS_FILE from the production files in ff-sdk-testgrid or create files with at least 18K unique targets
 * and 400 unique flags if you want to run this test.
 *
 * If running in IntelliJ you can attach JFR from a terminal after starting the test using something like:
 *
 *  jcmd  $(jcmd | grep -i junit | cut -f 1 -d " ")  JFR.start duration=10m filename=flight.jfr
 */
@Disabled
class MetricsProcessorStressTest {

  boolean RUN_PERPETUALLY = false; // useful for longer tests, note if true, test will never exit
  boolean DUMP_POSTED_METRICS = false;
  int NUM_THREADS = 32;
  int VARIATION_COUNT = 4;
  String TARGETS_FILE = "/tmp/prod2_targets.txt";
  String FLAGS_FILE = "/tmp/flags.txt";

  @Test
  void testRegisterEvaluationContention() throws Exception {

    final DummyConnector dummyConnector = new DummyConnector(DUMP_POSTED_METRICS);

    final MetricsProcessor metricsProcessor =
        new MetricsProcessor(
            dummyConnector,
            BaseConfig.builder()
                // .globalTargetEnabled(false)
                .build(),
            new DummyMetricsCallback());

    metricsProcessor.start();

    System.out.println("Loading...");

    final List<String> targets = loadFile(TARGETS_FILE);
    final List<String> flags = loadFile(FLAGS_FILE);

    System.out.printf("Loaded %d targets\n", targets.size());
    System.out.printf("Loaded %d flags\n", flags.size());

    final ConcurrentLinkedQueue<TargetAndFlag> targetAndFlags =
        createFlagTargetVariationPermutations(flags, targets);

    System.out.printf("Starting...processing %d flags/targets\n", targetAndFlags.size());

    final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    final LongAdder totalProcessed = new LongAdder();

    for (int i = 0; i < NUM_THREADS; i++) {
      final int threadNum = i;
      executor.submit(
          () -> {
            Thread.currentThread().setName("THREAD" + threadNum);
            System.out.println("start thread " + Thread.currentThread().getName());

            TargetAndFlag next;

            while ((next = targetAndFlags.poll()) != null) {
              final Target target = Target.builder().identifier(next.target).build();
              final FeatureConfig feature = FeatureConfig.builder().feature(next.flag).build();
              final Variation variation =
                  Variation.builder()
                      .identifier(next.variation)
                      .value(next.variation + "Value")
                      .build();

              metricsProcessor.registerEvaluation(target, feature.getFeature(), variation);

              if (RUN_PERPETUALLY) {
                targetAndFlags.add(next);
              }

              totalProcessed.increment();
            }

            System.out.printf("thread %s finished\n", Thread.currentThread().getName());
          });
    }

    while (targetAndFlags.size() > 0) {
      System.out.printf(
          "target/flags/variations processed %d, map size %d, pending evaluations=%d \n",
          totalProcessed.sum(),
          metricsProcessor.getQueueSize(),
          metricsProcessor.getPendingMetricsToBeSent());

      Thread.sleep(1000);
    }

    metricsProcessor.runOneIteration();

    assertEquals(flags.size() * targets.size() * VARIATION_COUNT, (int) totalProcessed.sum());
    assertEquals(
        flags.size() * targets.size() * VARIATION_COUNT,
        dummyConnector.getTotalMetricEvaluations());
  }

  private List<String> loadFile(String filename) throws IOException {
    final List<String> map = new ArrayList<>();
    try (Stream<String> stream = Files.lines(Paths.get(filename))) {
      stream.forEach(map::add);
    }
    return map;
  }

  private ConcurrentLinkedQueue<TargetAndFlag> createFlagTargetVariationPermutations(
      List<String> flags, List<String> targets) {
    final ConcurrentLinkedQueue<TargetAndFlag> targetAndFlags = new ConcurrentLinkedQueue<>();

    for (String flag : flags) {
      for (String target : targets) {
        for (int v = 0; v < VARIATION_COUNT; v++) { // variations per flag/target combination
          targetAndFlags.add(new TargetAndFlag(target, flag, "variation" + v));
        }
      }
    }
    return targetAndFlags;
  }

  @EqualsAndHashCode
  @AllArgsConstructor
  static class TargetAndFlag {
    String target, flag, variation;
  }

  static class DummyMetricsCallback implements MetricsCallback {
    @Override
    public void onMetricsReady() {
      System.out.println("onMetricsReady");
    }

    @Override
    public void onMetricsError(@NonNull String error) {
      System.out.println("onMetricsError " + error);
    }

    @Override
    public void onMetricsFailure() {
      System.out.println("onMetricsFailure");
    }
  }
}
