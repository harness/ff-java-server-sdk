package io.harness.cf.client.api;

import com.google.gson.Gson;
import io.harness.cf.client.connector.HarnessConfig;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.model.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.mockwebserver.MockResponse;

class TestUtils {

  @Data
  @AllArgsConstructor
  static class Event {
    String event, domain, identifier;
    int version;
  }

  static MockResponse makeMockJsonResponse(int httpCode, String body) {
    return new MockResponse()
        .setResponseCode(httpCode)
        .setBody(body)
        .addHeader("Content-Type", "application/json; charset=UTF-8");
  }

  static String makeSegmentsJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/segments.json");
  }

  static String makeFeatureJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/percentage-rollout-with-zero-weights.json");
  }

  static String makeBasicFeatureJson() throws IOException, URISyntaxException {
    return getJsonResource("local-test-cases/basic_bool_string_number_json_variations.json");
  }

  static MockResponse makeAuthResponse() {
    return makeMockJsonResponse(200, "{\"authToken\": \"" + makeDummyJwtToken() + "\"}");
  }

  static MockResponse makeMockStreamResponse(int httpCode, Event... events) {

    final StringBuilder builder = new StringBuilder();
    Arrays.stream(events)
        .forEach(
            e -> builder.append("event: *\ndata: ").append(new Gson().toJson(e)).append("\n\n"));

    return new MockResponse()
        .setResponseCode(httpCode)
        .setBody(builder.toString())
        .addHeader("Content-Type", "text/event-stream; charset=UTF-8")
        .addHeader("Accept-Encoding", "identity");
  }

  static MockResponse makeMockSingleBoolFlagResponse(
      int httpCode, String flagName, String state, int version) {
    final FeatureConfig flag = makeFlag(flagName, state, version);
    return makeMockJsonResponse(httpCode, new Gson().toJson(flag));
  }

  static HarnessConnector makeConnector(String host, int port) {
    final String url = String.format("http://%s:%s/api/1.0", host, port);
    return new HarnessConnector(
        "dummykey", HarnessConfig.builder().readTimeout(1000).configUrl(url).eventUrl(url).build());
  }

  static String makeDummyJwtToken() {
    final String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    final String payload =
        "{\"environment\":\"00000000-0000-0000-0000-000000000000\","
            + "\"environmentIdentifier\":\"Production\","
            + "\"project\":\"00000000-0000-0000-0000-000000000000\","
            + "\"projectIdentifier\":\"dev\","
            + "\"accountID\":\"aaaaa_BBBBB-cccccccccc\","
            + "\"organization\":\"00000000-0000-0000-0000-000000000000\","
            + "\"organizationIdentifier\":\"default\","
            + "\"clusterIdentifier\":\"1\","
            + "\"key_type\":\"Server\"}";
    final byte[] hmac256 = new byte[32];
    return Base64.getEncoder().encodeToString(header.getBytes(StandardCharsets.UTF_8))
        + "."
        + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
        + "."
        + Base64.getEncoder().encodeToString(hmac256);
  }

  static String getJsonResource(String location) throws IOException, URISyntaxException {
    final Path path = Paths.get(EvaluatorTest.class.getClassLoader().getResource(location).toURI());
    return new String(Files.readAllBytes(path));
  }

  static Event makeFlagPatchEvent(String identifier, int version) {
    return new Event("patch", "flag", identifier, version);
  }

  static FeatureConfig makeFlag(String flagName, String state, int version) {
    final FeatureConfig flag = new FeatureConfig();
    flag.setDefaultServe(new Serve().variation("true"));
    flag.setEnvironment("DUMMY");
    flag.setFeature(flagName);
    flag.setKind(FeatureConfig.KindEnum.BOOLEAN);
    flag.setOffVariation("false");
    flag.setPrerequisites(Collections.emptyList());
    flag.setProject("DUMMYPROJ");
    flag.setRules(Collections.emptyList());
    flag.setState(FeatureState.fromValue(state));
    flag.setVariationToTargetMap(Collections.emptyList());
    flag.setVariations(
        Arrays.asList(
            new Variation("true", "true", "True", "desc"),
            new Variation("false", "false", "False", "desc")));
    flag.setVersion((long) version);
    return flag;
  }
}
