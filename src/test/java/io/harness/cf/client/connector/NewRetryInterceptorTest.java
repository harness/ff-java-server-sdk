package io.harness.cf.client.connector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class NewRetryInterceptorTest {

  @Test
  void shouldStopImmediatelyWhenCancelledRequestFails() throws Exception {
    final Interceptor.Chain chain = mock(Interceptor.Chain.class);
    final Call call = mock(Call.class);
    final Request request = new Request.Builder().url("https://localhost/stream").build();
    when(chain.call()).thenReturn(call);
    when(chain.request()).thenReturn(request);
    when(chain.proceed(request))
        .thenAnswer(
            invocation -> {
              when(call.isCanceled()).thenReturn(true);
              throw new IOException("Canceled");
            });

    try (Response response =
        new NewRetryInterceptor(5_000L, true, new AtomicBoolean(false)).intercept(chain)) {
      assertEquals(400, response.code());
      assertTrue(response.message().contains("Canceled"));
      verify(chain).proceed(request);
    }
  }

  @Test
  @Timeout(30)
  void shouldStopRetryingWhenCallIsCancelled() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      for (int i = 0; i < 50; i++) server.enqueue(new MockResponse().setResponseCode(503));
      server.start();

      final OkHttpClient client =
          new OkHttpClient.Builder()
              .addInterceptor(new NewRetryInterceptor(60, 5_000L, new AtomicBoolean(false)))
              .build();
      final Call call = client.newCall(new Request.Builder().url(server.url("/")).build());

      final long start = System.nanoTime();
      new Thread(
              () -> {
                try {
                  Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                call.cancel();
              })
          .start();

      try {
        call.execute().close();
      } catch (IOException ignored) {
        // cancellation may surface as IOException
      }
      final long elapsedMs = (System.nanoTime() - start) / 1_000_000;

      // without the fix the 5s backoff would run (and keep retrying) after cancel
      assertTrue(elapsedMs < 3_000, "retry loop did not stop on cancel, took " + elapsedMs);
      assertTrue(server.getRequestCount() <= 2, "requests: " + server.getRequestCount());
    }
  }
}
