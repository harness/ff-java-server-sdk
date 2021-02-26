package io.harness.cf.client.api.rules;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DistributionProcessorTest {

  /*@Test
  public void shouldLoadKeyName() {
    Target target = Target.builder().email("username@harness.io").build();
    DistributionProcessor distributionProcessor =
        new DistributionProcessor(
            DistributionBuilder.aDistribution()
                .withBucketBy("email")
                .withVariations(
                    new ImmutableList.Builder<WeightedVariation>()
                        .add(
                            WeightedVariationBuilder.aWeightedVariation()
                                .withVariation("true")
                                .withWeight(20)
                                .build())
                        .add(
                            WeightedVariationBuilder.aWeightedVariation()
                                .withVariation("false")
                                .withWeight(80)
                                .build())
                        .build())
                .build());
    String result = distributionProcessor.loadKeyName(target);
    log.info("Result: {}", result);
  }*/
}
