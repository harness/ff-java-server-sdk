package io.harness.cf.client.api.dispatchers;

import static io.harness.cf.client.api.TestUtils.makeBasicFeatureJson;
import static io.harness.cf.client.api.TestUtils.makeSegmentsJson;
import static io.harness.cf.client.api.dispatchers.CannedResponses.*;
import static io.harness.cf.client.api.dispatchers.Endpoints.*;

import io.harness.cf.client.api.testutils.PollingAtomicLong;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.Headers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.jetbrains.annotations.NotNull;

public class JwtMissingFieldsAuthDispatcher extends TestWebServerDispatcher {
  private final AtomicInteger version = new AtomicInteger(2);
  @Getter private final PollingAtomicLong endpointsHit;
  @Getter private final List<Exception> errors = new ArrayList<>();

  private final String jwtEnvironmentIdentifier;
  private final String jwtAccountId;

  /* Set to null whatever fields you're testing in the JWT token */
  public JwtMissingFieldsAuthDispatcher(String jwtEnvironmentIdentifier, String jwtAccountId) {
    this.jwtEnvironmentIdentifier = jwtEnvironmentIdentifier;
    this.jwtAccountId = jwtAccountId;
    endpointsHit = new PollingAtomicLong(5);
  }

  @Override
  @SneakyThrows
  @NotNull
  public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
    System.out.printf(
        "DISPATCH GOT ------> %s  jwtEnvironmentIdentifier='%s' jwtAccountId='%s'\n",
        recordedRequest.getPath(), jwtEnvironmentIdentifier, jwtAccountId);

    endpointsHit.incrementAndGet();

    switch (Objects.requireNonNull(recordedRequest.getPath())) {
      case AUTH_ENDPOINT:
        return makeAuthResponse(
            200, "00000000-0000-0000-0000-000000000000", jwtEnvironmentIdentifier, jwtAccountId);
      case FEATURES_ENDPOINT:
        assertHeaders(recordedRequest);
        return makeMockJsonResponse(200, makeBasicFeatureJson());
      case SEGMENTS_ENDPOINT:
        assertHeaders(recordedRequest);
        return makeMockJsonResponse(200, makeSegmentsJson());
      case STREAM_ENDPOINT:
        assertHeaders(recordedRequest);
        return makeMockStreamResponse(
            200, makeFlagPatchEvent("simplebool", version.getAndIncrement()));
      case SIMPLE_BOOL_FLAG_ENDPOINT:
        assertHeaders(recordedRequest);
        return makeMockSingleBoolFlagResponse(200, "simplebool", "off", version.get());
        // TODO add metrics here
      default:
        throw new UnsupportedOperationException(
            "ERROR: url not mapped " + recordedRequest.getPath());
    }
  }

  private MockResponse makeAssertFailResp(String msg) {
    return new MockResponse()
        .setSocketPolicy(SocketPolicy.SHUTDOWN_SERVER_AFTER_RESPONSE)
        .setResponseCode(-1)
        .setStatus(msg);
  }

  private void assertHeaders(RecordedRequest recordedRequest) {
    final Headers headers = recordedRequest.getHeaders();
    final String url = recordedRequest.getPath();

    System.out.print(headers);

    final String accountVal = headers.get("Harness-AccountID");
    if (jwtAccountId == null || jwtAccountId.trim().isEmpty()) {
      if (accountVal != null) {
        errors.add(
            new RuntimeException(
                String.format(
                    "Harness-AccountID=%s header should not be present on req '%s'",
                    accountVal, url)));
      }
    } else {
      if (!jwtAccountId.equals(accountVal)) {
        errors.add(
            new RuntimeException(
                String.format(
                    "Harness-AccountID=%s header does not match JWT accountID '%s' on req '%s'",
                    accountVal, jwtAccountId, url)));
      }
    }

    final String envIdVal = headers.get("Harness-EnvironmentID");
    if (jwtEnvironmentIdentifier == null || jwtEnvironmentIdentifier.trim().isEmpty()) {
      if (!"00000000-0000-0000-0000-000000000000".equals(envIdVal)) {
        errors.add(
            new RuntimeException(
                String.format(
                    "Harness-EnvironmentID=%s header should fallback to UUID when environmentIdentifier is null on req '%s'",
                    envIdVal, url)));
      }
    } else {
      if (!jwtEnvironmentIdentifier.equals(envIdVal)) {
        errors.add(
            new RuntimeException(
                String.format(
                    "Harness-EnvironmentID=%s does not match JWT environmentIdentifier '%s' on req '%s'",
                    envIdVal, jwtEnvironmentIdentifier, url)));
      }
    }
  }

  public void waitForAllEndpointsToBeCalled(int waitTimeSeconds) throws InterruptedException {
    endpointsHit.waitForMinimumValueToBeReached(
        waitTimeSeconds,
        "auth/feat/seg/stream/flag",
        "Did not get minimum number of endpoint calls");
    Thread.sleep(500); // give time for resp to get back
  }
}
