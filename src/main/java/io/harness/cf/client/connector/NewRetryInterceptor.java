package io.harness.cf.client.connector;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

@Slf4j
public class NewRetryInterceptor implements Interceptor {

  private final long retryBackoffDelay;
  private final long maxTryCount;

  public NewRetryInterceptor(long retryBackoffDelay) {
    this.retryBackoffDelay = retryBackoffDelay;
    this.maxTryCount = 5;
  }

  public NewRetryInterceptor(long maxTryCount, long retryBackoffDelay) {
    this.retryBackoffDelay = retryBackoffDelay;
    this.maxTryCount = maxTryCount;
  }

  @NotNull
  @Override
  public Response intercept(@NotNull Chain chain) throws IOException {
    int tryCount = 1;
    boolean successful;
    Response response = null;
    String msg = "";
    do {
      try {
        if (response != null) response.close();

        response = chain.proceed(chain.request());
        successful = response.isSuccessful();
        if (!successful) {
          msg = String.format("httpCode=%d %s", response.code(), response.message());
          if (!shouldRetryHttpErrorCode(response.code())) {
            return response;
          }
        } else if (tryCount > 1) {
          log.info(
              "Connection to {} was successful after {} attempts", chain.request().url(), tryCount);
        }

      } catch (Exception ex) {
        log.trace("Error while attempting to make request", ex);
        msg = ex.getMessage();
        response = makeErrorResp(chain, msg);
        successful = false;
        if (!shouldRetryException(ex)) {
          return response;
        }
      }
      if (!successful) {
        final boolean limitReached = tryCount > maxTryCount;
        log.warn(
            "Request attempt {} to {} was not successful, [{}]{}",
            tryCount,
            chain.request().url(),
            msg,
            limitReached
                ? ", retry limited reached"
                : String.format(", retrying in %dms", retryBackoffDelay * tryCount));

        if (!limitReached) {
          sleep(retryBackoffDelay * tryCount);
        }
      }
    } while (!successful && tryCount++ <= maxTryCount);

    return response;
  }

  private boolean shouldRetryException(Exception ex) {
    return true;
  }

  private boolean shouldRetryHttpErrorCode(int httpCode) {
    if (httpCode == 501) return false;
    if (httpCode == 403) return false; // handled by a different interceptor

    return httpCode == 429 || httpCode == 408 || httpCode >= 500;
  }

  private Response makeErrorResp(Chain chain, String msg) {
    return new Response.Builder()
        .code(404) /* dummy response: real reason is in the message */
        .request(chain.request())
        .protocol(Protocol.HTTP_2)
        .message(msg)
        .body(ResponseBody.create("", MediaType.parse("text/plain")))
        .build();
  }

  private void sleep(long delayMs) {
    try {
      TimeUnit.MILLISECONDS.sleep(delayMs);
    } catch (InterruptedException e) {
      log.debug("Retry backoff interrupted", e);
      Thread.currentThread().interrupt();
    }
  }
}
