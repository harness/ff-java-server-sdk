package io.harness.cf.client.api.dispatchers;

import static io.harness.cf.client.api.TestUtils.makeBasicFeatureJson;
import static io.harness.cf.client.api.TestUtils.makeSegmentsJson;
import static io.harness.cf.client.api.dispatchers.CannedResponses.*;
import static io.harness.cf.client.api.dispatchers.Endpoints.*;

import io.harness.cf.client.api.testutils.PollingAtomicLong;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

public class Http4xxOnGetAllSegmentsDispatcher extends TestWebServerDispatcher {
  private final AtomicInteger version = new AtomicInteger(2);
  @Getter private final PollingAtomicLong authAttempts;
  @Getter private final PollingAtomicLong getSegmentsAttempts;

  public Http4xxOnGetAllSegmentsDispatcher(int minAuthAttempts, int minGetSegmentsAttempts) {
    authAttempts = new PollingAtomicLong(minAuthAttempts);
    getSegmentsAttempts = new PollingAtomicLong(minGetSegmentsAttempts);
  }

  private MockResponse makeSegmentsResp() throws IOException, URISyntaxException {
    System.out.println("getSegmentsAttempts " + getSegmentsAttempts.get());

    final long reqNo = getSegmentsAttempts.getAndIncrement();

    if (reqNo == 0) {
      return makeMockJsonResponse(200, makeSegmentsJson()); // 1st will succeed
    } else if (reqNo >= 1 && reqNo <= 4) {
      return makeMockEmptyJsonResponse(
          403,
          "Forbidden - reqNo "
              + reqNo); // Fail 2nd-4th request to bypass retries and trigger re-auth
    } else {
      return makeMockJsonResponse(
          200, makeSegmentsJson()); // 5th and subsequent requests will return 200s
    }
  }

  @Override
  @SneakyThrows
  @NotNull
  public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
    System.out.println("DISPATCH GOT ------> " + recordedRequest.getPath());

    switch (Objects.requireNonNull(recordedRequest.getPath())) {
      case AUTH_ENDPOINT:
        authAttempts.getAndIncrement();
        return makeAuthResponse(200);
      case FEATURES_ENDPOINT:
        return makeMockJsonResponse(200, makeBasicFeatureJson());
      case SEGMENTS_ENDPOINT:
        return makeSegmentsResp();
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
    getSegmentsAttempts.waitForMinimumValueToBeReached(
        waitTimeSeconds,
        "target-segments",
        "Did not get minimum number of target-segments connection attempts to "
            + SEGMENTS_ENDPOINT);
  }
}
