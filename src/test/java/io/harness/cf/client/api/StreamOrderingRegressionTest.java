package io.harness.cf.client.api;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.client.dto.Message;
import io.harness.cf.model.FeatureConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;

class StreamOrderingRegressionTest {
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
  void deliveredDeleteRunsAfterAnEarlierPatchFetch() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch applied = new CountDownLatch(2);
    RepositoryCallback callbacks = mock(RepositoryCallback.class);
    doAnswer(
            invocation -> {
              applied.countDown();
              return null;
            })
        .when(callbacks)
        .onFlagStored("gone");
    doAnswer(
            invocation -> {
              applied.countDown();
              return null;
            })
        .when(callbacks)
        .onFlagDeleted("gone");
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), callbacks, false);
    UpdateProcessor updater = new UpdateProcessor(connector, repository, mock(Updater.class));
    when(connector.getFlag("gone"))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(3, SECONDS));
              return flag("gone", 2);
            });
    try {
      updater.update(new Message("patch", "flag", "gone", 2));
      assertTrue(fetching.await(3, SECONDS));
      updater.update(new Message("delete", "flag", "gone", 3));
      // Give the old parallel worker a chance to apply the delete before the fetch completes.
      verify(callbacks, after(100).atMost(1)).onFlagDeleted("gone");
      release.countDown();
      assertTrue(applied.await(3, SECONDS));
      assertFalse(repository.getFlag("gone").isPresent());
    } finally {
      release.countDown();
      updater.close();
    }
  }

  @Test
  void streamAdmissionBlocksAtCapacityAndDeliversEveryAcceptedUpdate() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch processed = new CountDownLatch(1024);
    when(connector.getFlag(anyString()))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(5, SECONDS));
              processed.countDown();
              return null;
            });
    UpdateProcessor updater =
        new UpdateProcessor(
            connector,
            new StorageRepository(new CaffeineCache(100), null, false),
            mock(Updater.class));
    ExecutorService publisher = Executors.newSingleThreadExecutor();
    Future<?> publishing =
        publisher.submit(
            () -> {
              for (int i = 0; i < 1024; i++)
                updater.update(new Message("patch", "flag", "queued-" + i, 1));
            });
    try {
      assertTrue(fetching.await(3, SECONDS));
      assertThrows(TimeoutException.class, () -> publishing.get(200, TimeUnit.MILLISECONDS));
      release.countDown();
      publishing.get(5, SECONDS);
      assertTrue(processed.await(5, SECONDS));
    } finally {
      release.countDown();
      publisher.shutdownNow();
      updater.close();
    }
  }

  @Test
  void completeSnapshotSupersedesPreviouslyQueuedDeletes() throws Exception {
    Connector connector = mock(Connector.class);
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, false);
    UpdateProcessor updates = new UpdateProcessor(connector, repository, mock(Updater.class));
    PollingProcessor polling =
        new PollingProcessor(connector, repository, 60, mock(PollerCallback.class));
    CountDownLatch drained = new CountDownLatch(1);
    when(connector.getFlag("barrier"))
        .thenAnswer(
            invocation -> {
              drained.countDown();
              return null;
            });
    when(connector.getFlags()).thenReturn(Collections.singletonList(flag("recreated", 2)));
    when(connector.getSegments()).thenReturn(Collections.emptyList());
    Lock lock = repository.snapshotLock();
    try {
      lock.lock();
      try {
        updates.update(new Message("delete", "flag", "recreated", 1));
        assertTrue(polling.retrieveAll());
        updates.update(new Message("patch", "flag", "barrier", 1));
      } finally {
        lock.unlock();
      }
      assertTrue(drained.await(3, SECONDS));
      assertTrue(repository.getFlag("recreated").isPresent());
    } finally {
      updates.close();
      polling.close();
    }
  }

  @Test
  void failedSnapshotPreservesPreviouslyQueuedDeletes() throws Exception {
    Connector connector = mock(Connector.class);
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, false);
    repository.setFlag("gone", flag("gone", 1));
    UpdateProcessor updates = new UpdateProcessor(connector, repository, mock(Updater.class));
    PollingProcessor polling =
        new PollingProcessor(connector, repository, 60, mock(PollerCallback.class));
    CountDownLatch drained = new CountDownLatch(1);
    when(connector.getFlag("barrier"))
        .thenAnswer(
            invocation -> {
              drained.countDown();
              return null;
            });
    when(connector.getFlags()).thenThrow(new ConnectorException("synthetic snapshot failure"));
    Lock lock = repository.snapshotLock();
    try {
      lock.lock();
      try {
        updates.update(new Message("delete", "flag", "gone", 2));
        assertFalse(polling.retrieveAll());
        updates.update(new Message("patch", "flag", "barrier", 1));
      } finally {
        lock.unlock();
      }
      assertTrue(drained.await(3, SECONDS));
      assertFalse(repository.getFlag("gone").isPresent());
    } finally {
      updates.close();
      polling.close();
    }
  }

  @Test
  void eventReceivedDuringSnapshotFetchIsStillApplied() throws Exception {
    Connector connector = mock(Connector.class);
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), null, false);
    UpdateProcessor updates = new UpdateProcessor(connector, repository, mock(Updater.class));
    PollingProcessor polling =
        new PollingProcessor(connector, repository, 60, mock(PollerCallback.class));
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch drained = new CountDownLatch(1);
    when(connector.getFlag("barrier"))
        .thenAnswer(
            invocation -> {
              drained.countDown();
              return null;
            });
    when(connector.getFlags())
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(3, SECONDS));
              return Collections.singletonList(flag("gone", 1));
            });
    when(connector.getSegments()).thenReturn(Collections.emptyList());
    ExecutorService worker = Executors.newSingleThreadExecutor();
    try {
      Future<Boolean> refresh = worker.submit(polling::retrieveAll);
      assertTrue(fetching.await(3, SECONDS));
      updates.update(new Message("delete", "flag", "gone", 2));
      release.countDown();
      assertTrue(refresh.get(3, SECONDS));
      updates.update(new Message("patch", "flag", "barrier", 1));
      assertTrue(drained.await(3, SECONDS));
      assertFalse(repository.getFlag("gone").isPresent());
    } finally {
      release.countDown();
      worker.shutdownNow();
      updates.close();
      polling.close();
    }
  }

  @Test
  void closingReleasesPublishersWaitingForCapacity() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(connector.getFlag(anyString()))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              try {
                release.await(5, SECONDS);
              } catch (InterruptedException expected) {
                Thread.currentThread().interrupt();
                throw new ConnectorException("synthetic cancellation");
              }
              return null;
            });
    UpdateProcessor updates =
        new UpdateProcessor(
            connector,
            new StorageRepository(new CaffeineCache(100), null, false),
            mock(Updater.class));
    ExecutorService publisher = Executors.newSingleThreadExecutor();
    Future<?> publishing =
        publisher.submit(
            () -> {
              for (int i = 0; i < 1024; i++)
                updates.update(new Message("patch", "flag", "queued-" + i, 1));
            });
    try {
      assertTrue(fetching.await(3, SECONDS));
      assertThrows(TimeoutException.class, () -> publishing.get(100, TimeUnit.MILLISECONDS));
      updates.close();
      ExecutionException rejected =
          assertThrows(ExecutionException.class, () -> publishing.get(3, SECONDS));
      assertInstanceOf(RejectedExecutionException.class, rejected.getCause());
    } finally {
      release.countDown();
      updates.close();
      publisher.shutdownNow();
    }
  }

  @Test
  void aCallbackCannotDeadlockOnItsOwnFullQueue() throws Exception {
    Connector connector = mock(Connector.class);
    RepositoryCallback callbacks = mock(RepositoryCallback.class);
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), callbacks, false);
    UpdateProcessor updates = new UpdateProcessor(connector, repository, mock(Updater.class));
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch callbackDone = new CountDownLatch(1);
    when(connector.getFlag("first"))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(3, SECONDS));
              return flag("first", 1);
            });
    doAnswer(
            invocation -> {
              assertThrows(
                  RejectedExecutionException.class,
                  () -> updates.update(new Message("patch", "flag", "reentrant", 1)));
              callbackDone.countDown();
              return null;
            })
        .when(callbacks)
        .onFlagStored("first");
    try {
      updates.update(new Message("patch", "flag", "first", 1));
      assertTrue(fetching.await(3, SECONDS));
      for (int i = 0; i < 256; i++) updates.update(new Message("patch", "flag", "queued-" + i, 1));
      release.countDown();
      assertTrue(callbackDone.await(3, SECONDS));
    } finally {
      release.countDown();
      updates.close();
    }
  }

  @Test
  void segmentDeleteRunsAfterAnEarlierPatchFetch() throws Exception {
    Connector connector = mock(Connector.class);
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch applied = new CountDownLatch(2);
    RepositoryCallback callbacks = mock(RepositoryCallback.class);
    doAnswer(
            invocation -> {
              applied.countDown();
              return null;
            })
        .when(callbacks)
        .onSegmentStored("group");
    doAnswer(
            invocation -> {
              applied.countDown();
              return null;
            })
        .when(callbacks)
        .onSegmentDeleted("group");
    StorageRepository repository = new StorageRepository(new CaffeineCache(100), callbacks, false);
    UpdateProcessor updates = new UpdateProcessor(connector, repository, mock(Updater.class));
    when(connector.getSegment("group"))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(3, SECONDS));
              io.harness.cf.model.Segment segment = new io.harness.cf.model.Segment();
              segment.setIdentifier("group");
              segment.setVersion(2L);
              return segment;
            });
    try {
      updates.update(new Message("patch", "target-segment", "group", 2));
      assertTrue(fetching.await(3, SECONDS));
      updates.update(new Message("delete", "target-segment", "group", 3));
      release.countDown();
      assertTrue(applied.await(3, SECONDS));
      assertFalse(repository.getSegment("group").isPresent());
    } finally {
      release.countDown();
      updates.close();
    }
  }
}
