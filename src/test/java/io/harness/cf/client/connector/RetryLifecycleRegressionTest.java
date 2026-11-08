package io.harness.cf.client.connector;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import okio.BufferedSource;
import okio.ForwardingSource;
import okio.Okio;
import org.junit.jupiter.api.Test;

class RetryLifecycleRegressionTest {
  @Test
  void canceledCallsMustNotBeRetried() throws Exception {
    Interceptor.Chain chain = mock(Interceptor.Chain.class);
    Call call = mock(Call.class);
    AtomicInteger attempts = new AtomicInteger();
    when(call.isCanceled()).thenReturn(true);
    when(chain.call()).thenReturn(call);
    when(chain.request())
        .thenReturn(new Request.Builder().url("http://127.0.0.1/synthetic").build());
    when(chain.proceed(any()))
        .thenAnswer(
            invocation -> {
              attempts.incrementAndGet();
              throw new IOException("Canceled");
            });
    NewRetryInterceptor interceptor = new NewRetryInterceptor(3, 0, new AtomicBoolean(false));
    assertThrows(IOException.class, () -> interceptor.intercept(chain));
    assertEquals(0, attempts.get(), "An already canceled call must not reach transport");
  }

  @Test
  void closingAStreamDuring503RetriesMustReleaseItsHttpWorker() throws Exception {
    AtomicBoolean shuttingDown = new AtomicBoolean(false);
    try (MockWebServer server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
              return new MockResponse()
                  .setResponseCode(503)
                  .setHeader("Retry-After", "3600")
                  .setBody("synthetic unavailable");
            }
          });
      server.start();
      Updater callback = mock(Updater.class);
      EventSource stream =
          new EventSource(
              server.url("/synthetic-stream").toString(),
              Collections.emptyMap(),
              callback,
              1,
              20,
              null,
              shuttingDown);
      OkHttpClient client = null;
      try {
        stream.start();
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        Field field = EventSource.class.getDeclaredField("streamClient");
        field.setAccessible(true);
        client = (OkHttpClient) field.get(stream);
        stream.close();
        boolean terminated =
            client.dispatcher().executorService().awaitTermination(2, TimeUnit.SECONDS);
        assertTrue(
            terminated,
            "Closed stream retained an active retry call: "
                + client.dispatcher().runningCallsCount()
                + ", real HTTP requests: "
                + server.getRequestCount());
        verify(callback, never()).onDisconnected(anyString());
      } finally {
        shuttingDown.set(true);
        stream.close();
        if (client != null) {
          client.dispatcher().executorService().shutdownNow();
          assertTrue(
              client.dispatcher().executorService().awaitTermination(5, TimeUnit.SECONDS),
              "Synthetic stream worker did not stop after the client-wide shutdown signal");
        }
      }
    }
  }

  @Test
  void malformedStreamResponsesMustCloseTheirBody() throws Exception {
    AtomicBoolean closed = new AtomicBoolean();
    BufferedSource content =
        Okio.buffer(
            new ForwardingSource(new Buffer().writeUtf8("data: {broken}\n\n")) {
              @Override
              public void close() throws IOException {
                closed.set(true);
                super.close();
              }
            });
    ResponseBody body =
        new ResponseBody() {
          public MediaType contentType() {
            return MediaType.parse("text/event-stream");
          }

          public long contentLength() {
            return -1;
          }

          public BufferedSource source() {
            return content;
          }
        };
    Response response =
        new Response.Builder()
            .request(new Request.Builder().url("http://127.0.0.1/synthetic-stream").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body)
            .build();
    Updater updater = mock(Updater.class);
    EventSource stream =
        new EventSource("http://127.0.0.1/synthetic-stream", Collections.emptyMap(), updater, 1);
    try {
      stream.onResponse(mock(Call.class), response);
      verify(updater).onDisconnected(anyString());
      assertTrue(closed.get(), "Stream error returned without closing its response body");
    } finally {
      response.close();
      stream.close();
    }
  }

  @Test
  void interruptingBackoffPropagatesCancellationAndPreservesInterrupt() throws Exception {
    Interceptor.Chain chain = mock(Interceptor.Chain.class);
    when(chain.call()).thenReturn(mock(Call.class));
    Request request = new Request.Builder().url("http://127.0.0.1/retry").build();
    when(chain.request()).thenReturn(request);
    CountDownLatch attempted = new CountDownLatch(1);
    when(chain.proceed(any()))
        .thenAnswer(
            invocation -> {
              attempted.countDown();
              return new Response.Builder()
                  .request(request)
                  .protocol(Protocol.HTTP_1_1)
                  .code(503)
                  .message("Unavailable")
                  .body(ResponseBody.create("", MediaType.parse("text/plain")))
                  .build();
            });
    AtomicBoolean shutdown = new AtomicBoolean();
    AtomicBoolean interrupted = new AtomicBoolean();
    AtomicReference<IOException> failure = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              try (Response response =
                  new NewRetryInterceptor(3, 60000, shutdown).intercept(chain)) {
              } catch (IOException ex) {
                failure.set(ex);
                interrupted.set(Thread.currentThread().isInterrupted());
              }
            });
    try {
      worker.start();
      assertTrue(attempted.await(2, TimeUnit.SECONDS));
      worker.interrupt();
      worker.join(2000);
      assertFalse(worker.isAlive());
      assertInstanceOf(InterruptedIOException.class, failure.get());
      assertTrue(interrupted.get());
      verify(chain, times(1)).proceed(any());
    } finally {
      shutdown.set(true);
      worker.interrupt();
      worker.join(2000);
    }
  }

  @Test
  void clientShutdownCancelsAnActiveConfigurationRequest() throws Exception {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE));
      server.start();
      HarnessConnector connector =
          new HarnessConnector(
              "synthetic-key",
              HarnessConfig.builder()
                  .configUrl(server.url("/api").toString())
                  .readTimeout(5000)
                  .maxRequestRetry(1)
                  .build());
      String claims = "{\"environment\":\"synthetic-env\",\"clusterIdentifier\":\"1\"}";
      connector.processToken(
          "header."
              + java.util.Base64.getUrlEncoder()
                  .withoutPadding()
                  .encodeToString(claims.getBytes(java.nio.charset.StandardCharsets.UTF_8))
              + ".signature");
      java.util.concurrent.ExecutorService worker =
          java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        java.util.concurrent.Future<?> request =
            worker.submit(
                () -> {
                  connector.getFlags();
                  return null;
                });
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
        connector.setIsShuttingDown();
        java.util.concurrent.ExecutionException canceled =
            assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> request.get(2, TimeUnit.SECONDS));
        assertInstanceOf(ConnectorException.class, canceled.getCause());
        assertEquals(1, server.getRequestCount());
      } finally {
        connector.close();
        worker.shutdownNow();
      }
    }
  }
}
