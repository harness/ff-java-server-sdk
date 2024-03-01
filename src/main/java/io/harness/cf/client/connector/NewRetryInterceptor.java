package io.harness.cf.client.connector;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NewRetryInterceptor implements Interceptor {

  private static final Logger log = LoggerFactory.getLogger(NewRetryInterceptor.class);
  private static final SimpleDateFormat imfDateFormat =
      new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
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
    boolean limitReached = false;
    Response response = null;
    String msg = "";
    do {
      try {
        if (response != null) response.close();

        response = chain.proceed(chain.request());
        successful = response.isSuccessful();
        if (!successful) {
          msg =
              String.format(
                  Locale.getDefault(), "httpCode=%d %s", response.code(), response.message());
          if (!shouldRetryHttpErrorCode(response.code())) {
            return response;
          }
        } else if (tryCount > 1) {
          log.info(
              "Connection to {} was successful after {} attempts", chain.request().url(), tryCount);
        }

      } catch (Exception ex) {
        log.trace("Error while attempting to make request", ex);
        msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        response = makeErrorResp(chain, msg);
        successful = false;
        if (!shouldRetryException(ex)) {
          return response;
        }
      }
      if (!successful) {
        int retryAfterHeaderValue = getRetryAfterHeaderInSeconds(response);
        long backOffDelayMs;
        if (retryAfterHeaderValue > 0) {
          // Use Retry-After header if detected first
          log.trace("Retry-After header detected: {} seconds", retryAfterHeaderValue);
          backOffDelayMs = retryAfterHeaderValue * 1000L;
        } else {
          // Else fallback to a randomized exponential backoff
          backOffDelayMs = retryBackoffDelay * tryCount;
        }

        limitReached = tryCount >= maxTryCount;
        log.warn(
            "Request attempt {} to {} was not successful, [{}]{}",
            tryCount,
            chain.request().url(),
            msg,
            limitReached
                ? ", retry limited reached"
                : String.format(
                    Locale.getDefault(),
                    ", retrying in %dms  (retry-after hdr: %b)",
                    backOffDelayMs,
                    retryAfterHeaderValue > 0));

        if (!limitReached) {
          sleep(backOffDelayMs);
        }
      }
      tryCount++;
    } while (!successful && !limitReached);

    return response;
  }

  int getRetryAfterHeaderInSeconds(Response response) {
    if (response.code() != 503
        && response.code() != 429
        && !(response.code() >= 300 && response.code() <= 399)) {
      return 0;
    }

    final String retryAfterValue = response.header("Retry-After");
    if (retryAfterValue == null) {
      return 0;
    }

    int seconds = 0;
    try {
      seconds = Integer.parseInt(retryAfterValue);
    } catch (NumberFormatException ignored) {
    }

    if (seconds <= 0) {
      try {
        final Date then = imfDateFormat.parse(retryAfterValue);
        if (then != null) {
          seconds = (int) Duration.between(Instant.now(), then.toInstant()).getSeconds();
        }
      } catch (ParseException ignored) {
      }
    }

    if (seconds < 0) {
      seconds = 0;
    }

    return Math.min(seconds, 3600);
  }

  private boolean shouldRetryException(Exception ex) {
    log.info("should retry exception check: {}", ex.getMessage());
    return true;
  }

  private boolean shouldRetryHttpErrorCode(int httpCode) {
    if (httpCode == 501) return false;
    if (httpCode == 403) return false; // handled by a different interceptor

    return httpCode == 429 || httpCode == 408 || httpCode >= 500;
  }

  private Response makeErrorResp(Chain chain, String msg) {
    return new Response.Builder()
        .code(400) /* dummy response: real reason is in the message */
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
