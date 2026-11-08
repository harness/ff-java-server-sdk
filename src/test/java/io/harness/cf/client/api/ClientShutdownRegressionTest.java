package io.harness.cf.client.api;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.harness.cf.client.common.Storage;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.model.FeatureConfig;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClientShutdownRegressionTest {
  private static Object field(Object owner, String name) throws Exception {
    Class<?> type = owner instanceof Class ? (Class<?>) owner : owner.getClass();
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner instanceof Class ? null : owner);
  }

  private static FeatureConfig flag(String name, long version) throws Exception {
    String json =
        new String(
            Files.readAllBytes(
                Paths.get(
                    "src/test/resources/local-test-cases/basic_bool_string_for_repository.json")),
            StandardCharsets.UTF_8);
    FeatureConfig flag = new Gson().fromJson(json, FeatureConfig.class);
    flag.setFeature(name);
    flag.setVersion(version);
    return flag;
  }

  @Test
  void storeCloseFailureMustNotSkipNetworkAndWorkerCleanup() throws Exception {
    Connector connector = mock(Connector.class);
    Storage store = mock(Storage.class);
    doThrow(new IllegalStateException("synthetic store close failure")).when(store).close();
    InnerClient client =
        new InnerClient(
            connector,
            BaseConfig.builder().streamEnabled(false).analyticsEnabled(false).store(store).build());
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(3), client::waitForInitialization);
      assertThrows(IllegalStateException.class, client::close);
      verify(connector).close();
    } finally {
      ((AuthService) field(client, "authService")).close();
      ((PollingProcessor) field(client, "pollProcessor")).close();
      ((UpdateProcessor) field(client, "updateProcessor")).close();
      ((MetricsProcessor) field(client, "metricsProcessor")).close();
      connector.close();
    }
  }

  @Test
  void pollingFinishesBeforeStorageCloses() throws Exception {
    Connector connector = mock(Connector.class);
    Storage store = mock(Storage.class);
    AtomicBoolean closed = new AtomicBoolean();
    AtomicInteger lateWrites = new AtomicInteger();
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(connector.getFlags())
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              try {
                release.await(3, SECONDS);
              } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
              }
              return singletonList(flag("late", 1));
            });
    when(connector.getSegments()).thenReturn(emptyList());
    doAnswer(
            invocation -> {
              closed.set(true);
              return null;
            })
        .when(store)
        .close();
    doAnswer(
            invocation -> {
              if (closed.get()) lateWrites.incrementAndGet();
              return null;
            })
        .when(store)
        .set(anyString(), any());
    InnerClient client =
        new InnerClient(
            connector,
            BaseConfig.builder().streamEnabled(false).analyticsEnabled(false).store(store).build());
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      assertTrue(fetching.await(3, SECONDS));
      Future<?> closing = worker.submit(client::close);
      closing.get(3, SECONDS);
      assertEquals(0, lateWrites.get());
      verify(store).close();
      verify(connector).close();
    } finally {
      release.countDown();
      worker.shutdownNow();
      client.close();
    }
  }

  @Test
  void closedRepositoryRejectsLateMutations() throws Exception {
    Storage store = mock(Storage.class);
    StorageRepository repository =
        new StorageRepository(new CaffeineCache(100), store, null, false);
    repository.close();
    assertThrows(IllegalStateException.class, () -> repository.setFlag("late", flag("late", 1)));
    assertThrows(IllegalStateException.class, () -> repository.deleteFlag("late"));
    verify(store, never()).set(anyString(), any());
    verify(store, never()).delete(anyString());
  }

  @Test
  void cleanupAttemptsEveryResourceAndRepeatedCloseIsSafe() throws Exception {
    Connector connector = mock(Connector.class);
    Storage store = mock(Storage.class);
    doThrow(new IllegalArgumentException("connector close failure")).when(connector).close();
    doThrow(new IllegalStateException("store close failure")).when(store).close();
    InnerClient client =
        new InnerClient(
            connector,
            BaseConfig.builder().streamEnabled(false).analyticsEnabled(false).store(store).build());
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(3), client::waitForInitialization);
      RuntimeException failure = assertThrows(RuntimeException.class, client::close);
      assertEquals(1, failure.getSuppressed().length);
      assertDoesNotThrow(client::close);
      verify(store, times(1)).close();
      verify(connector, times(1)).close();
    } finally {
      ((AuthService) field(client, "authService")).close();
      ((PollingProcessor) field(client, "pollProcessor")).close();
      ((UpdateProcessor) field(client, "updateProcessor")).close();
      ((MetricsProcessor) field(client, "metricsProcessor")).close();
    }
  }

  @Test
  void readyCallbacksDoNotHoldTheClientMonitor() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch callbackDone = new CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Throwable> callbackFailure =
        new java.util.concurrent.atomic.AtomicReference<>();
    when(connector.getFlags())
        .thenAnswer(
            invocation -> {
              assertTrue(release.await(3, SECONDS));
              return emptyList();
            });
    InnerClient client =
        new InnerClient(
            connector, BaseConfig.builder().streamEnabled(false).analyticsEnabled(false).build());
    ExecutorService worker = Executors.newSingleThreadExecutor();
    client.on(
        Event.READY,
        ignored -> {
          try {
            worker
                .submit(
                    () -> {
                      client.waitForInitialization();
                      return null;
                    })
                .get(1, SECONDS);
          } catch (Exception ex) {
            callbackFailure.set(ex);
          } finally {
            callbackDone.countDown();
          }
        });
    try {
      release.countDown();
      assertTrue(callbackDone.await(3, SECONDS));
      assertNull(callbackFailure.get());
    } finally {
      release.countDown();
      worker.shutdownNow();
      client.close();
    }
  }

  @Test
  void readyCallbackCanCloseTheClientWithoutWaitingForItself() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch closed = new CountDownLatch(1);
    when(connector.getFlags())
        .thenAnswer(
            invocation -> {
              assertTrue(release.await(3, SECONDS));
              return emptyList();
            });
    InnerClient client =
        new InnerClient(
            connector, BaseConfig.builder().streamEnabled(false).analyticsEnabled(false).build());
    client.on(
        Event.READY,
        ignored -> {
          client.close();
          closed.countDown();
        });
    try {
      release.countDown();
      assertTrue(closed.await(3, SECONDS));
      verify(connector).close();
    } finally {
      release.countDown();
      client.close();
    }
  }
}
