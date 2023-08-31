package io.harness.cf.client.api.dispatchers;

import io.harness.cf.client.api.testutils.PollingAtomicLong;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

public class Http403OnAuthDispatcher extends Dispatcher {

  private final PollingAtomicLong connectAttempts;

  public Http403OnAuthDispatcher(int minConnectToWaitFor) {
    connectAttempts = new PollingAtomicLong(minConnectToWaitFor);
  }

  @Getter final ConcurrentHashMap<String, Long> urlMap = new ConcurrentHashMap<>();

  @Override
  @SneakyThrows
  public MockResponse dispatch(RecordedRequest recordedRequest) {
    System.out.println("DISPATCH GOT ------> " + recordedRequest.getPath());
    connectAttempts.getAndIncrement();
    incrementKey(urlMap, Objects.requireNonNull(recordedRequest.getPath()));
    return new MockResponse().setResponseCode(403);
  }

  private void incrementKey(ConcurrentHashMap<String, Long> map, String key) {
    map.compute(key, (k, v) -> (v == null) ? 1L : v + 1L);
  }

  public void waitForAllConnections(int waitTimeSeconds) throws InterruptedException {
    connectAttempts.waitForMinimumValueToBeReached(
        waitTimeSeconds, "any", "Did not get minimum number of connection attempts");
  }
}
