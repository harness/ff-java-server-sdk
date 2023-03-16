package io.harness.cf.client.api.dispatchers;

import com.google.common.util.concurrent.AtomicLongMap;
import io.harness.cf.client.api.testutils.PollingAtomicLong;
import java.util.Objects;
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

  @Getter final AtomicLongMap<String> urlMap = AtomicLongMap.create();

  @Override
  @SneakyThrows
  public MockResponse dispatch(RecordedRequest recordedRequest) {
    System.out.println("DISPATCH GOT ------> " + recordedRequest.getPath());
    connectAttempts.getAndIncrement();
    urlMap.incrementAndGet(Objects.requireNonNull(recordedRequest.getPath()));
    return new MockResponse().setResponseCode(403);
  }

  public void waitForAllConnections(int waitTimeSeconds) throws InterruptedException {
    connectAttempts.waitForMinimumValueToBeReached(
        waitTimeSeconds, "any", "Did not get minimum number of connection attempts");
  }
}
