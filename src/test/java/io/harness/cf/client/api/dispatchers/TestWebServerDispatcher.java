package io.harness.cf.client.api.dispatchers;

import static io.harness.cf.client.api.TestUtils.makeBasicFeatureJson;
import static io.harness.cf.client.api.TestUtils.makeSegmentsJson;
import static io.harness.cf.client.api.dispatchers.CannedResponses.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

public class TestWebServerDispatcher extends Dispatcher {
  private final AtomicInteger version = new AtomicInteger(2);

  @Override
  @SneakyThrows
  @NotNull
  public MockResponse dispatch(@NotNull RecordedRequest recordedRequest)
      throws InterruptedException {
    System.out.println("DISPATCH GOT ------> " + recordedRequest.getPath());

    switch (Objects.requireNonNull(recordedRequest.getPath())) {
      case "/api/1.0/client/auth":
        return makeAuthResponse();
      case "/api/1.0/client/env/00000000-0000-0000-0000-000000000000/feature-configs?cluster=1":
        return makeMockJsonResponse(200, makeBasicFeatureJson());
      case "/api/1.0/client/env/00000000-0000-0000-0000-000000000000/target-segments?cluster=1&rules=v2":
        return makeMockJsonResponse(200, makeSegmentsJson());
      case "/api/1.0/stream?cluster=1":
        return makeMockStreamResponse(
            200, makeFlagPatchEvent("simplebool", version.getAndIncrement()));
      case "/api/1.0/client/env/00000000-0000-0000-0000-000000000000/feature-configs/simplebool?cluster=1":
        return makeMockSingleBoolFlagResponse(200, "simplebool", "off", version.get());
      default:
        throw new UnsupportedOperationException(
            "ERROR: url not mapped " + recordedRequest.getPath());
    }
  }
}
