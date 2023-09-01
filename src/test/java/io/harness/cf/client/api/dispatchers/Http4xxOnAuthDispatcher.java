package io.harness.cf.client.api.dispatchers;

import io.harness.cf.client.api.testutils.PollingAtomicLong;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.SneakyThrows;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

public class Http4xxOnAuthDispatcher extends Dispatcher {

  private final PollingAtomicLong connectAttempts;
  private final int httpCodeToReturn;

  public Http4xxOnAuthDispatcher(int httpCodeToReturn, int minConnectToWaitFor) {
    this.httpCodeToReturn = httpCodeToReturn;
    connectAttempts = new PollingAtomicLong(minConnectToWaitFor);
  }

  @Getter final ConcurrentHashMap<String, Long> urlMap = new ConcurrentHashMap<>();

  @Override
  @SneakyThrows
  public MockResponse dispatch(RecordedRequest recordedRequest) {
    System.out.println("DISPATCH GOT ------> " + recordedRequest.getPath());
    connectAttempts.getAndIncrement();
    incrementKey(urlMap, Objects.requireNonNull(recordedRequest.getPath()));
    return new MockResponse().setResponseCode(httpCodeToReturn);
  }

  private void incrementKey(ConcurrentHashMap<String, Long> map, String key) {
    map.compute(key, (k, v) -> (v == null) ? 1L : v + 1L);
  }

  public void waitForAllConnections(int waitTimeSeconds) throws InterruptedException {
    connectAttempts.waitForMinimumValueToBeReached(
        waitTimeSeconds, "any", "Did not get minimum number of connection attempts");
  }
}
