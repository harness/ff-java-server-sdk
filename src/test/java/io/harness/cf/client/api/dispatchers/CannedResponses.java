package io.harness.cf.client.api.dispatchers;

import static java.lang.String.format;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.FeatureState;
import io.harness.cf.model.Serve;
import io.harness.cf.model.Variation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.mockwebserver.MockResponse;

public class CannedResponses {

  @Data
  @AllArgsConstructor
  static class Event {
    String event, domain, identifier;
    int version;
  }

  public static MockResponse makeMockJsonResponse(int httpCode, String body) {
    return new MockResponse()
        .setResponseCode(httpCode)
        .setBody(body)
        .addHeader("Content-Type", "application/json; charset=UTF-8");
  }

  public static MockResponse makeAuthResponse() {
    return makeAuthResponse(200);
  }

  public static MockResponse makeAuthResponse(int httpCode) {
    return makeMockJsonResponse(httpCode, "{\"authToken\": \"" + makeDummyJwtToken() + "\"}");
  }

  public static MockResponse makeMockStreamResponse(int httpCode, Event... events) {

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

  public static MockResponse makeMockEmptyJsonResponse(int httpCode) {
    return new MockResponse().setResponseCode(httpCode);
  }

  public static MockResponse makeMockEmptyJsonResponse(int httpCode, String httpReason) {
    return new MockResponse().setStatus(format("HTTP/1.1 %d %s", httpCode, httpReason));
  }

  public static MockResponse makeMockSingleBoolFlagResponse(
      int httpCode, String flagName, String state, int version) {
    final FeatureConfig flag = makeFlag(flagName, state, version);
    return makeMockJsonResponse(httpCode, new Gson().toJson(flag));
  }

  public static CannedResponses.Event makeFlagPatchEvent(String identifier, int version) {
    return new CannedResponses.Event("patch", "flag", identifier, version);
  }

  public static String makeDummyJwtToken() {
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

  public static FeatureConfig makeFlag(String flagName, String state, int version) {
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
