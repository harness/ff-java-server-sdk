package io.harness.cf.client.api;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TestUtils {

  public static String makeSegmentsJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/segments.json");
  }

  public static String makeFeatureJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/percentage-rollout-with-zero-weights.json");
  }

  public static String makeBasicFeatureJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/basic_bool_string_number_json_variations.json");
  }

  public static String makeBasicFeatureJsonForRepoTest() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/basic_bool_string_for_repository.json");
  }

  public static String getJsonResource(String location) throws IOException, URISyntaxException {
    final Path path = Paths.get(EvaluatorTest.class.getClassLoader().getResource(location).toURI());
    return new String(Files.readAllBytes(path));
  }
}
