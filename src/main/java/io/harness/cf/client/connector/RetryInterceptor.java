package io.harness.cf.client.connector;

import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@AllArgsConstructor
class RetryInterceptor implements Interceptor {
  private final long maxTryCount;
  private final long retryBackoffDelay;

  @NonNull
  @Override
  public Response intercept(@NonNull Chain chain) throws IOException {
    Request request = chain.request();
    Response response = chain.proceed(request);

    int tryCount = 0;
    while (!response.isSuccessful() && tryCount < maxTryCount) {
      log.debug("Request is not successful - {}", tryCount);

      tryCount++;

      try {
        Thread.sleep(retryBackoffDelay * tryCount);
      } catch (InterruptedException e) {
        log.debug("Request is not successful - {}", tryCount, e);
      }
      // retry the request
      response = chain.proceed(request);
    }
    return response;
  }
}
