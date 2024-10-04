package io.harness.cf.client.connector;

import static io.harness.cf.client.api.dispatchers.CannedResponses.*;
import static java.lang.System.out;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

      if (reqNo <= 12) {
        // Force a disconnect after the default SDK request retry limit of 10, which does not apply
        // to stream requests which have
        // no limit on retryable errors
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
      // Set a 400 response so that the stream does not retry.  This is because since 1.8.0 the
      // stream
      // retries forever on retryable errors.
      return new MockResponse().setResponseCode(400).setBody("{\"status\":\"failed\"}");
    }
  }

  @Test
  void shouldNotCallErrorHandlerIfRetryEventuallyReconnectsToStreamEndpoint()
      throws IOException, InterruptedException, ConnectorException {
    CountingUpdater updater = new CountingUpdater();

    try (MockWebServer mockSvr = new MockWebServer();
        EventSource eventSource =
            new EventSource(
                setupMockServer(mockSvr, new StreamDispatcher()),
                new HashMap<>(),
                updater,
                1,
                1,
                null,
                new AtomicBoolean(false))) {
      eventSource.start();

      TimeUnit.SECONDS.sleep(15);
    }

    // for this test, connection to the /stream endpoint will fail several times but eventually
    // connect. There should be at least 1 connect event.

    assertTrue(updater.getConnectCount().get() >= 1);
    assertEquals(0, updater.getFailureCount().get());
  }

  @Test
  void shouldRestartPollerIfAllConnectionAttemptsToStreamEndpointFail()
      throws IOException, InterruptedException, ConnectorException {
    CountingUpdater updater = new CountingUpdater();

    try (MockWebServer mockSvr = new MockWebServer();
        EventSource eventSource =
            new EventSource(
                setupMockServer(mockSvr, new FailingStreamDispatcher()),
                new HashMap<>(),
                updater,
                1,
                1,
                null,
                new AtomicBoolean(false))) {
      eventSource.start();

      TimeUnit.SECONDS.sleep(3);
    }

    // for this test, connection to the /stream endpoint will never because of an un-retryable
    // error. We expect the disconnect handler to be called, connect handler should not be called

    assertEquals(0, updater.getConnectCount().get());
    assertEquals(0, updater.getFailureCount().get());
    assertTrue(updater.getDisconnectCount().get() >= 1);
  }

  @SneakyThrows
  private String setupMockServer(MockWebServer mockSvr, Dispatcher dispatcher) {
    mockSvr.setDispatcher(dispatcher);
    mockSvr.start();
    return String.format(
        "http://%s:%s/api/1.0/stream?cluster=%d", mockSvr.getHostName(), mockSvr.getPort(), 1);
  }
}
