package io.harness.cf.client.connector;

import static io.harness.cf.client.api.dispatchers.CannedResponses.*;
import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.*;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

@Slf4j
class EventSourceTest {

  private final AtomicInteger version = new AtomicInteger(2);

  static class StreamDispatcher extends Dispatcher {
    private final AtomicInteger version = new AtomicInteger(2);
    protected final AtomicInteger request = new AtomicInteger(1);

    protected MockResponse makeStreamResponse() {
      int reqNo = request.getAndIncrement();
      if (reqNo <= 3) {
        // Force a disconnect on the first few attempts
        out.printf("ReqNo %d will be disconnected on purpose\n", reqNo);
        return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
      } else {
        // Eventually allow a good connection attempt and send a flag
        out.printf("ReqNo %d will be allowed\n", reqNo);
        return makeMockStreamResponse(
                200,
                makeFlagPatchEvent("simplebool", version.getAndIncrement()),
                makeFlagPatchEvent("simplebool", version.getAndIncrement()))
            .setSocketPolicy(SocketPolicy.KEEP_OPEN);
      }
    }

    @Override
    @SneakyThrows
    @NotNull
    public MockResponse dispatch(RecordedRequest recordedRequest) {
      out.println("DISPATCH GOT ------> " + recordedRequest.getPath());

      // recordedRequest.getHeaders().forEach(h -> out.printf("    %s: %s\n", h.component1(),
      // h.component2()));

      if (Objects.requireNonNull(recordedRequest.getPath()).equals("/api/1.0/stream?cluster=1")) {
        return makeStreamResponse();
      }
      throw new UnsupportedOperationException("ERROR: url not mapped " + recordedRequest.getPath());
    }
  }

  static class FailingStreamDispatcher extends StreamDispatcher {
    @Override
    protected MockResponse makeStreamResponse() {
      int reqNo = request.getAndIncrement();
      // Force a disconnect on all requests
      out.printf("ReqNo %d will be disconnected on purpose\n", reqNo);
      return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
    }
  }

  @Test
  void shouldNotCallErrorHandlerIfRetryEventuallyReconnectsToStreamEndpoint()
      throws IOException, InterruptedException {
    CountingUpdater updater = new CountingUpdater();

    try (MockWebServer mockSvr = new MockWebServer();
        EventSource eventSource =
            new EventSource(
                setupMockServer(mockSvr, new StreamDispatcher()), new HashMap<>(), updater, 1, 1)) {
      eventSource.start();

      TimeUnit.SECONDS.sleep(15);
    }

    // for this test, connection to the /stream endpoint will fail several times but eventually
    // connect.
    // There should be at least 1 connect event. Each retry calls onError

    assertTrue(updater.getConnectCount().get() >= 1);
    assertEquals(0, updater.getFailureCount().get());
    assertEquals(5, updater.getErrorCount().get());
  }

  @Test
  void shouldRestartPollerIfAllConnectionAttemptsToStreamEndpointFail()
      throws IOException, InterruptedException {
    CountingUpdater updater = new CountingUpdater();

    try (MockWebServer mockSvr = new MockWebServer();
        EventSource eventSource =
            new EventSource(
                setupMockServer(mockSvr, new FailingStreamDispatcher()),
                new HashMap<>(),
                updater,
                1,
                1)) {
      eventSource.start();

      TimeUnit.SECONDS.sleep(15);
    }

    // for this test, connection to the /stream endpoint will never succeed.
    // we expect the error handler to be called, connect handler should not be called

    assertEquals(0, updater.getConnectCount().get());
    assertEquals(0, updater.getFailureCount().get());
    assertTrue(updater.getErrorCount().get() >= 1);
  }

  @SneakyThrows
  private String setupMockServer(MockWebServer mockSvr, Dispatcher dispatcher) {
    mockSvr.setDispatcher(dispatcher);
    mockSvr.start();
    return String.format(
        "http://%s:%s/api/1.0/stream?cluster=%d", mockSvr.getHostName(), mockSvr.getPort(), 1);
  }
}
