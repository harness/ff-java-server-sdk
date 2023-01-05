package io.harness.cf.client.api;

import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
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

  public static HarnessConnector makeConnector(String host, int port) {
    final String url = String.format("http://%s:%s/api/1.0", host, port);
    return new HarnessConnector(
        "dummykey", HarnessConfig.builder().readTimeout(1000).configUrl(url).eventUrl(url).build());
  }

  public static String getJsonResource(String location) throws IOException, URISyntaxException {
    final Path path = Paths.get(EvaluatorTest.class.getClassLoader().getResource(location).toURI());
    return new String(Files.readAllBytes(path));
  }
}
