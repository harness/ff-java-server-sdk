package io.harness.cf.client.api;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.harness.cf.ApiException;
import io.harness.cf.api.DefaultApi;
import io.harness.cf.client.dto.*;
import io.harness.cf.model.FeatureFlagActivationConfig;
import io.harness.cf.model.FeatureState;

import java.io.IOException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import javax.management.modelmbean.XMLParseException;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientTest {
    private static final String FEATURE_FLAG_KEY = "my-boolean-flag";
    String apiKey = "99932cb4-65ce-4d7a-b47a-892d78ee7666";
    private Target target;
    private List<FeatureConfig> featureConfigs;
    @Spy private final DefaultApi defaultApi = new DefaultApi();
    private CfClient cfClient = new CfClient(apiKey);

    public ClientTest() throws CfClientException, XMLParseException, IOException {}

    @Before
    public void setUp() {
        target =
                Target.builder()
                        .firstName("Hannah")
                        .lastName("Tang")
                        .email("username@harness.io")
                        .country("USA")
                        .custom(
                                new ImmutableMap.Builder<String, Object>().put("accountId", "ACCOUNT_ID").build())
                        .build();
    }

    @Test
    public void testBoolVariation() {
        FeatureConfig featureConfig =
                FeatureConfigBuilder.aFeatureConfig()
                        .kind(FeatureConfig.KindEnum.BOOLEAN)
                        .state(FeatureState.ON)
                        .rules(
                                singletonList(
                                        ServingRuleBuilder.aServingRule()
                                                .clauses(
                                                        singletonList(
                                                                ClauseBuilder.aClause()
                                                                        .withAttribute("country")
                                                                        .withOp("match")
                                                                        .withValue(singletonList("USA"))
                                                                        .build()))
                                                .serve(
                                                        ServeBuilder.aServe()
                                                                .distribution(
                                                                        DistributionBuilder.aDistribution()
                                                                                .withBucketBy("email")
                                                                                .withVariations(
                                                                                        asList(
                                                                                                WeightedVariationBuilder.aWeightedVariation()
                                                                                                        .withVariation("true")
                                                                                                        .withWeight(40)
                                                                                                        .build(),
                                                                                                WeightedVariationBuilder.aWeightedVariation()
                                                                                                        .withVariation("false")
                                                                                                        .withWeight(60)
                                                                                                        .build()))
                                                                                .build())
                                                                .build())
                                                .build()))
                        .offVariation("false")
                        .build();

        featureFlagActivationConfigs =
                new ArrayList<>(singletonList(featureFlagActivationConfig));

        boolean result = cfClient.boolVariation(FEATURE_FLAG_KEY, target, false);
        assertThat(result).isTrue();
    }
  /*
  @Test
  public void testStringVariation() throws ApiException {
    FeatureFlagActivationConfig stringFeatureFlagActivationConfig =
        FeatureFlagActivationConfigBuilder.aFeatureFlagActivationConfig()
            .withKind(FeatureFlagActivationConfig.KindEnum.STRING)
            .withState(FeatureFlagState.ON)
            .withTargetRules(
                Collections.singletonList(
                    TargetRuleBuilder.aTargetRule()
                        .withClauses(
                            Collections.singletonList(
                                ClauseBuilder.aClause()
                                    .withAttribute("country")
                                    .withOp("match")
                                    .withValue("USA")
                                    .build()))
                        .withTargetDistribution(
                            TargetDistributionBuilder.aTargetDistribution()
                                .withBucketBy("email")
                                .withVariations(
                                    Arrays.asList(
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("one")
                                            .withWeight(10)
                                            .build(),
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("two")
                                            .withWeight(30)
                                            .build(),
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("three")
                                            .withWeight(60)
                                            .build()))
                                .build())
                        .build()))
            .withVariations(
                new ImmutableList.Builder<Variation>()
                    .add(VariationBuilder.aVariation().withKey("one").withValue("one").build())
                    .add(VariationBuilder.aVariation().withKey("two").withValue("two").build())
                    .add(VariationBuilder.aVariation().withKey("three").withValue("three").build())
                    .build())
            .withOffVariation("false")
            .build();
    featureFlagActivationConfigs =
        new ArrayList<>(Collections.singletonList(stringFeatureFlagActivationConfig));
    when(defaultApi.getFeatureFlagConfig(any(UUID.class))).thenReturn(featureFlagActivationConfigs);
    when(featureCache.get(eq(FEATURE_FLAG_KEY))).thenReturn(stringFeatureFlagActivationConfig);

    String result = cfClient.stringVariation(FEATURE_FLAG_KEY, target, "one");
    assertThat(result).isEqualTo("two");
  }

  @Test
  public void testNumberVariation() throws ApiException {
    FeatureFlagActivationConfig stringFeatureFlagActivationConfig =
        FeatureFlagActivationConfigBuilder.aFeatureFlagActivationConfig()
            .kind(FeatureFlagActivationConfig.KindEnum.INT)
            .state(FeatureState.ON)
            .withTargetRules(
                Collections.singletonList(
                    TargetRuleBuilder.aTargetRule()
                        .withClauses(
                            Collections.singletonList(
                                ClauseBuilder.aClause()
                                    .withAttribute("country")
                                    .withOp("match")
                                    .withValue("USA")
                                    .build()))
                        .withTargetDistribution(
                            TargetDistributionBuilder.aTargetDistribution()
                                .withBucketBy("email")
                                .withVariations(
                                    Arrays.asList(
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("0")
                                            .withWeight(10)
                                            .build(),
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("1")
                                            .withWeight(30)
                                            .build(),
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("2")
                                            .withWeight(60)
                                            .build()))
                                .build())
                        .build()))
            .withVariations(
                new ImmutableList.Builder<Variation>()
                    .add(VariationBuilder.aVariation().withKey("0").withValue(0.0).build())
                    .add(VariationBuilder.aVariation().withKey("1").withValue(100.0).build())
                    .add(VariationBuilder.aVariation().withKey("2").withValue(200.0).build())
                    .build())
            .withOffVariation("false")
            .build();
    featureFlagActivationConfigs =
        new ArrayList<>(Collections.singletonList(stringFeatureFlagActivationConfig));
    when(defaultApi.getFeatureFlagConfig(any(UUID.class))).thenReturn(featureFlagActivationConfigs);
    when(featureCache.get(eq(FEATURE_FLAG_KEY))).thenReturn(stringFeatureFlagActivationConfig);

    double result = cfClient.numberVariation(FEATURE_FLAG_KEY, target, 1);
    assertThat(result).isEqualTo(100);
  }

  @Test
  public void testJsonVariation() throws ApiException {
    Gson gson = new Gson();
    JsonObject jsonObject1 = new JsonObject();
    jsonObject1.add("name", new JsonPrimitive("enver"));
    JsonObject jsonObject2 = new JsonObject();
    jsonObject2.add("name", new JsonPrimitive("dusan"));

    FeatureFlagActivationConfig jsonFeatureFlagActivationConfig =
        FeatureFlagActivationConfigBuilder.aFeatureFlagActivationConfig()
            .kind(JSON)
            .state(FeatureState.ON)
            .rules(
                Collections.singletonList(
                    TargetRuleBuilder.aTargetRule()
                        .withClauses(
                            Collections.singletonList(
                                ClauseBuilder.aClause()
                                    .withAttribute("country")
                                    .withOp("match")
                                    .withValue("USA")
                                    .build()))
                        .withTargetDistribution(
                            TargetDistributionBuilder.aTargetDistribution()
                                .withBucketBy("email")
                                .withVariations(
                                    Arrays.asList(
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("enver")
                                            .withWeight(10)
                                            .build(),
                                        WeightedVariationBuilder.aWeightedVariation()
                                            .withVariation("dusan")
                                            .withWeight(90)
                                            .build()))
                                .build())
                        .build()))
            .withVariations(
                new ImmutableList.Builder<Variation>()
                    .add(
                        VariationBuilder.aVariation()
                            .withKey("enver")
                            .withValue(jsonObject1)
                            .build())
                    .add(
                        VariationBuilder.aVariation()
                            .withKey("dusan")
                            .withValue(jsonObject2)
                            .build())
                    .build())
            .withOffVariation("false")
            .build();

    featureFlagActivationConfigs =
        new ArrayList<>(Collections.singletonList(jsonFeatureFlagActivationConfig));
    when(defaultApi.getFeatureFlagConfig(any(UUID.class))).thenReturn(featureFlagActivationConfigs);
    when(featureCache.get(eq(FEATURE_FLAG_KEY))).thenReturn(jsonFeatureFlagActivationConfig);

    JsonObject result = cfClient.jsonVariation(FEATURE_FLAG_KEY, target, new JsonObject());
    assertThat(result).isEqualTo(jsonObject2);
  }*/
}
