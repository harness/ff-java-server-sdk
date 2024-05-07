package io.harness.cf.client.api.dispatchers;

import static io.harness.cf.client.api.dispatchers.CannedResponses.makeMockEmptyJsonResponse;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

public class UnimplementedStreamDispatcher extends TestWebServerDispatcher {

  private static final String STREAM_ENDPOINT = "/api/1.0/stream?cluster=1";
  @Getter private final AtomicInteger streamEndpointCount = new AtomicInteger(0);
  private final int maxConnectionAttempts;

  public UnimplementedStreamDispatcher(int maxConnectionAttempts) {
    this.maxConnectionAttempts = maxConnectionAttempts;
  }

  @Override
  @SneakyThrows
  public MockResponse dispatch(RecordedRequest recordedRequest) {
    if (STREAM_ENDPOINT.equals(recordedRequest.getPath())) {
      System.out.println("Returning 501 for stream endpoint");
      streamEndpointCount.incrementAndGet();
      return makeMockEmptyJsonResponse(501); // return 501 unimplemented on /api/1.0/stream
    }
    return super.dispatch(recordedRequest);
  }

  public void waitForAllConnections(int waitTimeSeconds) throws InterruptedException {
    final int delayMs = 100;
    int maxWaitTime = (waitTimeSeconds * 1000) / delayMs;
    while (maxWaitTime > 0) {
      if (streamEndpointCount.get() > maxConnectionAttempts) {
        fail(
            "Too many connection attempts to "
                + STREAM_ENDPOINT
                + " = "
                + streamEndpointCount.get());
      }

      System.out.printf(
          "Waiting for connections: got %d of %d...\n",
          streamEndpointCount.get(), maxConnectionAttempts);
      Thread.sleep(delayMs);
      maxWaitTime--;
    }

    if (streamEndpointCount.get() == 0) {
      fail("Did not get any connection attempts to " + STREAM_ENDPOINT);
    }
  }
}
