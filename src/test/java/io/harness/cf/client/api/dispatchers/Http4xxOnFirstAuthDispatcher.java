package io.harness.cf.client.api.dispatchers;

import static io.harness.cf.client.api.TestUtils.makeBasicFeatureJson;
import static io.harness.cf.client.api.TestUtils.makeSegmentsJson;
import static io.harness.cf.client.api.dispatchers.CannedResponses.*;
import static io.harness.cf.client.api.dispatchers.Endpoints.*;

import io.harness.cf.client.api.testutils.PollingAtomicLong;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

public class Http4xxOnFirstAuthDispatcher extends TestWebServerDispatcher {
  private final AtomicInteger version = new AtomicInteger(2);
  @Getter private final PollingAtomicLong authAttempts;

  public Http4xxOnFirstAuthDispatcher(int minAuthAttempts) {
    authAttempts = new PollingAtomicLong(minAuthAttempts);
  }

  private MockResponse dispatchAuthResp() {
    System.out.println("DISPATCH authAttempts = " + authAttempts.get());

    if (authAttempts.getAndIncrement() < 4) {
      System.out.println("--> 408");
      return makeAuthResponse(408);
    }
    System.out.println("--> 200");
    return makeAuthResponse(200);
  }

  @Override
  @SneakyThrows
  @NotNull
  public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
    System.out.println("DISPATCH GOT ------> " + recordedRequest.getPath());

    switch (Objects.requireNonNull(recordedRequest.getPath())) {
      case AUTH_ENDPOINT:
        return dispatchAuthResp();
      case FEATURES_ENDPOINT:
        return makeMockJsonResponse(200, makeBasicFeatureJson());
      case SEGMENTS_ENDPOINT:
        return makeMockJsonResponse(200, makeSegmentsJson());
      case STREAM_ENDPOINT:
        return makeMockStreamResponse(
            200, makeFlagPatchEvent("simplebool", version.getAndIncrement()));
      case SIMPLE_BOOL_FLAG_ENDPOINT:
        return makeMockSingleBoolFlagResponse(200, "simplebool", "off", version.get());
      default:
        throw new UnsupportedOperationException(
            "ERROR: url not mapped " + recordedRequest.getPath());
    }
  }

  public void waitForAllConnections(int waitTimeSeconds) throws InterruptedException {
    authAttempts.waitForMinimumValueToBeReached(
        waitTimeSeconds,
        "auth",
        "Did not get minimum number of auth connection attempts to " + AUTH_ENDPOINT);
  }
}
