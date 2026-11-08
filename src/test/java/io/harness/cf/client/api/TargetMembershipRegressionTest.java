package io.harness.cf.client.api;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import io.harness.cf.model.ServingRule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class TargetMembershipRegressionTest {
  private static FeatureConfig flag(String name, long version) throws Exception {
    String json =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/local-test-cases/basic_bool_string_for_repository.json")),
            StandardCharsets.UTF_8);
    FeatureConfig flag = new Gson().fromJson(json, FeatureConfig.class);
    flag.setFeature(name);
    flag.setVersion(version);
    return flag;
  }

  private static io.harness.cf.model.Target member(String identifier) {
    io.harness.cf.model.Target target = new io.harness.cf.model.Target();
    target.setIdentifier(identifier);
    return target;
  }

  private static Evaluator segmentEvaluator(String included, String excluded) throws Exception {
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, false);
    Segment group = new Segment();
    group.setIdentifier("group");
    group.setVersion(1L);
    group.setIncluded(included == null ? emptyList() : singletonList(member(included)));
    group.setExcluded(excluded == null ? emptyList() : singletonList(member(excluded)));
    repository.setSegment("group", group);
    FeatureConfig flag = flag("segment-flag", 1);
    flag.getDefaultServe().setVariation("false");
    flag.setRules(
        singletonList(
            new Gson()
                .fromJson(
                    "{\"ruleId\":\"rule\",\"priority\":1,\"serve\":{\"variation\":\"true\"},"
                        + "\"clauses\":[{\"op\":\"segmentMatch\",\"attribute\":\"identifier\",\"values\":[\"group\"]}]}",
                    ServingRule.class)));
    repository.setFlag("segment-flag", flag);
    return new Evaluator(repository, BaseConfig.builder().build());
  }

  @Test
  void includedTargetIdentifiersMustMatchExactly() throws Exception {
    Evaluator evaluator = segmentEvaluator("customer-12345", null);
    assertTrue(
        evaluator.boolVariation(
            "segment-flag", Target.builder().identifier("customer-12345").build(), false, null));
    assertFalse(
        evaluator.boolVariation(
            "segment-flag", Target.builder().identifier("123").build(), false, null),
        "Target 123 must not inherit membership from customer-12345");
  }

  @Test
  void excludedTargetIdentifiersMustMatchExactly() throws Exception {
    Evaluator evaluator = segmentEvaluator("123", "customer-12345");
    assertTrue(
        evaluator.boolVariation(
            "segment-flag", Target.builder().identifier("123").build(), false, null),
        "Target 123 must not inherit exclusion from customer-12345");
  }
}
