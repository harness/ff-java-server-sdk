package io.harness.cf.client.api;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.harness.cf.client.common.Storage;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.ConnectorException;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.client.dto.Message;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.GroupServingRule;
import io.harness.cf.model.Segment;
import io.harness.cf.model.ServingRule;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SnapshotReconciliationTest {
  private final Connector connector = mock(Connector.class);
  private final CaffeineCache cache = new CaffeineCache(10000);
  private final RepositoryCallback callback = mock(RepositoryCallback.class);
  private final StorageRepository repository = new StorageRepository(cache, callback, true);
  private final PollerCallback pollerCallback = mock(PollerCallback.class);
  private final PollingProcessor poller =
      new PollingProcessor(connector, repository, 1, pollerCallback);
  private final UpdateProcessor updater =
      new UpdateProcessor(connector, repository, mock(Updater.class));
  private final ExecutorService executor = Executors.newFixedThreadPool(3);

  @AfterEach
  void close() {
    executor.shutdownNow();
    updater.close();
    poller.close();
  }

  @Test
  void removesMissedDeletesAndPreviousVersions() throws Exception {
    repository.setFlag("gone", flag("gone", 1));
    repository.setFlag("gone", flag("gone", 2));
    repository.setSegment("gone-group", segment("gone-group"));
    when(connector.getFlags()).thenReturn(singletonList(flag("kept", 1)));
    when(connector.getSegments()).thenReturn(singletonList(segment("kept-group")));

    poller.retrieveAll();

    assertFalse(repository.getFlag("gone").isPresent());
    assertNull(cache.get("previous/gone"));
    assertFalse(repository.getSegment("gone-group").isPresent());
    assertTrue(repository.getFlag("kept").isPresent());
    assertTrue(repository.getSegment("kept-group").isPresent());
    verify(callback).onFlagDeleted("gone");
    verify(callback).onSegmentDeleted("gone-group");
  }

  @Test
  void acceptsAuthoritativeEmptySnapshotsAndKeepsUnrelatedKeys() throws Exception {
    repository.setFlag("gone", flag("gone", 1));
    repository.setSegment("gone-group", segment("gone-group"));
    cache.set("previous/orphan", flag("orphan", 1));
    cache.set("custom/value", "retained");
    when(connector.getFlags()).thenReturn(emptyList());
    when(connector.getSegments()).thenReturn(emptyList());

    poller.retrieveAll();

    assertEquals(singletonList("custom/value"), cache.keys());
  }

  @Test
  void preservesBothDomainsWhenTheSecondRequestFails() throws Exception {
    repository.setFlag("existing", flag("existing", 1));
    repository.setSegment("existing-group", segment("existing-group"));
    when(connector.getFlags()).thenReturn(Arrays.asList(flag("existing", 2), flag("new", 1)));
    when(connector.getSegments()).thenThrow(new ConnectorException("synthetic HTTP 503"));
    clearInvocations(callback);

    poller.retrieveAll();

    assertEquals(1L, repository.getFlag("existing").get().getVersion());
    assertFalse(repository.getFlag("new").isPresent());
    assertNull(cache.get("previous/existing"));
    assertTrue(repository.getSegment("existing-group").isPresent());
    verifyNoInteractions(callback);
  }

  @Test
  void preservesBothDomainsWhenTheFirstRequestFails() throws Exception {
    repository.setSegment("existing-group", segment("existing-group"));
    when(connector.getFlags()).thenThrow(new ConnectorException("synthetic HTTP 503"));
    when(connector.getSegments()).thenReturn(singletonList(segment("new-group")));

    poller.retrieveAll();

    assertTrue(repository.getSegment("existing-group").isPresent());
    assertFalse(repository.getSegment("new-group").isPresent());
  }

  @Test
  void rejectsMalformedSnapshotsBeforeChangingEitherDomain() throws Exception {
    repository.setFlag("existing", flag("existing", 1));
    repository.setSegment("existing-group", segment("existing-group"));
    when(connector.getFlags()).thenReturn(singletonList(flag("new", 1)));
    List<List<Segment>> malformed =
        Arrays.asList(
            null,
            Arrays.asList(segment("new-group"), null),
            singletonList(segment(" ")),
            Arrays.asList(segment("duplicate"), segment("duplicate")));
    for (List<Segment> segments : malformed) {
      when(connector.getSegments()).thenReturn(segments);
      poller.retrieveAll();
      assertTrue(repository.getFlag("existing").isPresent());
      assertFalse(repository.getFlag("new").isPresent());
      assertTrue(repository.getSegment("existing-group").isPresent());
      assertFalse(repository.getSegment("new-group").isPresent());
    }
  }

  @Test
  void reconcilesStoreKeysAndCacheOnlyPreviousVersions() throws Exception {
    MemoryStorage store = new MemoryStorage();
    StorageRepository stored = new StorageRepository(cache, store, callback, true);
    PollingProcessor storedPoller =
        new PollingProcessor(connector, stored, 1, mock(PollerCallback.class));
    try {
      stored.setFlag("gone", flag("gone", 1));
      stored.setFlag("gone", flag("gone", 2));
      stored.setSegment("gone-group", segment("gone-group"));
      cache.set("previous/cache-orphan", flag("cache-orphan", 1));
      store.set("custom/value", "retained");
      when(connector.getFlags()).thenReturn(emptyList());
      when(connector.getSegments()).thenReturn(emptyList());

      storedPoller.retrieveAll();

      assertEquals(singletonList("custom/value"), store.keys());
      assertTrue(cache.keys().isEmpty());
      assertFalse(stored.getFlag("gone").isPresent());
      assertFalse(stored.getSegment("gone-group").isPresent());
    } finally {
      storedPoller.close();
    }
  }

  @Test
  void callbacksObserveTheCompletedSnapshot() throws Exception {
    repository.setFlag("gone", flag("gone", 1));
    repository.setSegment("gone-group", segment("gone-group"));
    when(connector.getFlags()).thenReturn(singletonList(flag("new", 1)));
    when(connector.getSegments()).thenReturn(singletonList(segment("new-group")));
    AtomicReference<List<Boolean>> observed = new AtomicReference<>();
    doAnswer(
            invocation -> {
              observed.set(
                  Arrays.asList(
                      repository.getFlag("gone").isPresent(),
                      repository.getSegment("gone-group").isPresent(),
                      repository.getSegment("new-group").isPresent()));
              return null;
            })
        .when(callback)
        .onFlagStored("new");

    poller.retrieveAll();

    verify(callback).onFlagStored("new");
    assertEquals(Arrays.asList(false, false, true), observed.get());
  }

  @Test
  void serializesStreamFetchesWithRefreshAndKeepsReadsAvailable() throws Exception {
    repository.setFlag("existing", flag("existing", 1));
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch updateStarted = new CountDownLatch(1);
    when(connector.getFlags())
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(5, SECONDS));
              return singletonList(flag("existing", 2));
            });
    when(connector.getSegments()).thenReturn(emptyList());
    when(connector.getFlag("new-streamed")).thenReturn(flag("new-streamed", 3));
    Future<?> refresh = executor.submit(() -> poller.retrieveAll());
    try {
      assertTrue(fetching.await(5, SECONDS));
      Future<?> update =
          executor.submit(
              () -> {
                updateStarted.countDown();
                updater.processFlag(new Message("patch", "flag", "new-streamed", 3)).run();
              });
      assertTrue(updateStarted.await(5, SECONDS));
      assertThrows(TimeoutException.class, () -> update.get(100, TimeUnit.MILLISECONDS));
      assertEquals(
          1L,
          executor.submit(() -> repository.getFlag("existing").get().getVersion()).get(1, SECONDS));
      verify(connector, never()).getFlag("new-streamed");
      release.countDown();
      refresh.get(5, SECONDS);
      update.get(5, SECONDS);
      assertEquals(3L, repository.getFlag("new-streamed").get().getVersion());
    } finally {
      release.countDown();
    }
  }

  @Test
  void repeatedIdentifierChurnDoesNotAccumulateEntries() throws Exception {
    for (int cycle = 0; cycle < 30; cycle++) {
      when(connector.getFlags()).thenReturn(singletonList(flag("flag-" + cycle, 1)));
      when(connector.getSegments()).thenReturn(singletonList(segment("group-" + cycle)));
      poller.retrieveAll();
      assertEquals(2, cache.keys().size());
    }
  }

  @Test
  void preservesEvaluationsOnFailureAndUsesDefaultsAfterDeletion() throws Exception {
    Evaluator evaluator = new Evaluator(repository, BaseConfig.builder().build());
    repository.setFlag("existing", flag("existing", 1));
    assertTrue(evaluator.boolVariation("existing", null, false, null));
    when(connector.getFlags()).thenReturn(emptyList());
    when(connector.getSegments()).thenThrow(new ConnectorException("synthetic HTTP 503"));

    assertFalse(poller.retrieveAll());
    assertTrue(evaluator.boolVariation("existing", null, false, null));
    doReturn(emptyList()).when(connector).getSegments();
    assertTrue(poller.retrieveAll());
    assertFalse(evaluator.boolVariation("existing", null, false, null));
  }

  @Test
  void rejectsUnsortableSegmentsBeforeDeletingCachedFlags() throws Exception {
    repository.setFlag("existing", flag("existing", 1));
    Segment invalid = segment("invalid");
    invalid.setServingRules(Arrays.asList(new GroupServingRule(), new GroupServingRule()));
    when(connector.getFlags()).thenReturn(emptyList());
    when(connector.getSegments()).thenReturn(singletonList(invalid));

    assertFalse(poller.retrieveAll());
    assertTrue(repository.getFlag("existing").isPresent());
    assertFalse(repository.getSegment("invalid").isPresent());
  }

  @Test
  void keepsNewerVersionsAndTheirPreviousSnapshots() throws Exception {
    repository.setFlag("kept", flag("kept", 1));
    repository.setFlag("kept", flag("kept", 3));
    when(connector.getFlags()).thenReturn(singletonList(flag("kept", 2)));
    when(connector.getSegments()).thenReturn(emptyList());

    assertTrue(poller.retrieveAll());
    assertEquals(3L, repository.getFlag("kept").get().getVersion());
    assertEquals(1L, ((FeatureConfig) cache.get("previous/kept")).getVersion());
  }

  @Test
  void initializesOnlyAfterACompleteRefreshAndRetriesFailures() throws Exception {
    AtomicBoolean failing = new AtomicBoolean(true);
    when(connector.getFlags()).thenReturn(singletonList(flag("existing", 1)));
    when(connector.getSegments())
        .thenAnswer(
            invocation -> {
              if (failing.get()) throw new ConnectorException("synthetic HTTP 503");
              return emptyList();
            });

    poller.start();
    verify(pollerCallback, timeout(2000)).onPollerFailed(any());
    verify(pollerCallback, never()).onPollerReady();
    assertFalse(repository.getFlag("existing").isPresent());
    failing.set(false);
    verify(pollerCallback, timeout(3000)).onPollerReady();
    assertTrue(poller.retrieveAll());
    verify(pollerCallback, times(1)).onPollerReady();
  }

  @Test
  void reconnectKeepsPollingAfterFailureAndStopsAfterSuccess() throws Exception {
    PollingProcessor reconnectPoller = mock(PollingProcessor.class);
    InnerClient client = mock(InnerClient.class, CALLS_REAL_METHODS);
    Field field = InnerClient.class.getDeclaredField("pollProcessor");
    field.setAccessible(true);
    field.set(client, reconnectPoller);
    when(reconnectPoller.isRunning()).thenReturn(true);
    when(reconnectPoller.retrieveAll()).thenReturn(false, true);

    client.onConnected();
    verify(reconnectPoller, never()).stop();
    client.onConnected();
    verify(reconnectPoller).stop();
  }

  @Test
  void refreshWaitsForAnEarlierStreamFetchBeforeReconciling() throws Exception {
    CountDownLatch fetching = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch refreshStarted = new CountDownLatch(1);
    when(connector.getFlag("gone"))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(5, SECONDS));
              return flag("gone", 1);
            });
    when(connector.getFlags()).thenReturn(emptyList());
    when(connector.getSegments()).thenReturn(emptyList());
    Future<?> update =
        executor.submit(updater.processFlag(new Message("create", "flag", "gone", 1)));
    try {
      assertTrue(fetching.await(5, SECONDS));
      Future<?> refresh =
          executor.submit(
              () -> {
                refreshStarted.countDown();
                return poller.retrieveAll();
              });
      assertTrue(refreshStarted.await(5, SECONDS));
      assertThrows(TimeoutException.class, () -> refresh.get(100, TimeUnit.MILLISECONDS));
      verify(connector, never()).getFlags();
      release.countDown();
      update.get(5, SECONDS);
      refresh.get(5, SECONDS);
      assertFalse(repository.getFlag("gone").isPresent());
    } finally {
      release.countDown();
    }
  }

  @Test
  void streamFetchesCanStillRunInParallel() throws Exception {
    CountDownLatch fetching = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    when(connector.getFlag(anyString()))
        .thenAnswer(
            invocation -> {
              fetching.countDown();
              assertTrue(release.await(5, SECONDS));
              return flag(invocation.getArgument(0), 1);
            });
    Future<?> first =
        executor.submit(updater.processFlag(new Message("create", "flag", "first", 1)));
    Future<?> second =
        executor.submit(updater.processFlag(new Message("create", "flag", "second", 1)));
    try {
      assertTrue(fetching.await(2, SECONDS));
      release.countDown();
      first.get(5, SECONDS);
      second.get(5, SECONDS);
      assertTrue(repository.getFlag("first").isPresent());
      assertTrue(repository.getFlag("second").isPresent());
    } finally {
      release.countDown();
    }
  }

  @Test
  void streamCallbacksCanWaitForARefreshWithoutHoldingWriterLocks() throws Exception {
    FeatureConfig streamed = flag("streamed", 1);
    when(connector.getFlag("streamed")).thenReturn(streamed);
    when(connector.getFlags()).thenReturn(singletonList(streamed));
    when(connector.getSegments()).thenReturn(emptyList());
    AtomicReference<Throwable> failure = new AtomicReference<>();
    doAnswer(
            invocation -> {
              try {
                assertTrue(executor.submit(() -> poller.retrieveAll()).get(2, SECONDS));
              } catch (Exception ex) {
                failure.set(ex);
              }
              return null;
            })
        .when(callback)
        .onFlagStored("streamed");

    updater.processFlag(new Message("create", "flag", "streamed", 1)).run();

    verify(callback).onFlagStored("streamed");
    assertNull(failure.get());
  }

  @Test
  void oneFailedCallbackDoesNotPreventRemainingNotifications() throws Exception {
    when(connector.getFlags()).thenReturn(Arrays.asList(flag("first", 1), flag("second", 1)));
    when(connector.getSegments()).thenReturn(emptyList());
    doThrow(new IllegalStateException("synthetic listener failure"))
        .when(callback)
        .onFlagStored("first");

    assertTrue(poller.retrieveAll());
    verify(callback).onFlagStored("second");
    verify(pollerCallback).onPollerReady();
    assertTrue(repository.getFlag("first").isPresent());
    assertTrue(repository.getFlag("second").isPresent());
  }

  @Test
  void cancellationStopsAnUpdateWaitingForARefresh() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    repository.snapshotLock().lock();
    try {
      Future<?> update =
          executor.submit(
              () -> {
                started.countDown();
                updater.processFlag(new Message("patch", "flag", "waiting", 1)).run();
              });
      assertTrue(started.await(1, SECONDS));
      update.cancel(true);
      executor.shutdown();
      assertTrue(executor.awaitTermination(2, SECONDS));
      verify(connector, never()).getFlag("waiting");
    } finally {
      repository.snapshotLock().unlock();
    }
  }

  @Test
  void cancellationStopsARefreshWaitingForAStreamUpdate() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    repository.updateLock().lock();
    try {
      Future<?> refresh =
          executor.submit(
              () -> {
                started.countDown();
                return poller.retrieveAll();
              });
      assertTrue(started.await(1, SECONDS));
      refresh.cancel(true);
      executor.shutdown();
      assertTrue(executor.awaitTermination(2, SECONDS));
      verify(connector, never()).getFlags();
    } finally {
      repository.updateLock().unlock();
    }
  }

  @Test
  void segmentDeletionUpdatesEvaluationAndFindsDependentFlags() throws Exception {
    FeatureConfig dependent = flag("dependent", 1);
    dependent.getDefaultServe().setVariation("false");
    ServingRule rule =
        new Gson()
            .fromJson(
                "{\"ruleId\":\"rule\",\"priority\":1,\"serve\":{\"variation\":\"true\"},"
                    + "\"clauses\":[{\"op\":\"segmentMatch\",\"attribute\":\"identifier\",\"values\":[\"group\"]}]}",
                ServingRule.class);
    dependent.setRules(singletonList(rule));
    Segment group = segment("group");
    io.harness.cf.model.Target member = new io.harness.cf.model.Target();
    member.setIdentifier("member");
    group.setIncluded(singletonList(member));
    repository.setSegment("group", group);
    repository.setFlag("dependent", dependent);
    Evaluator evaluator = new Evaluator(repository, BaseConfig.builder().build());
    io.harness.cf.client.dto.Target target =
        io.harness.cf.client.dto.Target.builder().identifier("member").build();
    assertTrue(evaluator.boolVariation("dependent", target, false, null));
    when(connector.getFlags()).thenReturn(singletonList(dependent));
    when(connector.getSegments()).thenReturn(emptyList());
    AtomicReference<List<String>> notified = new AtomicReference<>();
    doAnswer(
            invocation -> {
              notified.set(repository.findFlagsBySegment("group"));
              return null;
            })
        .when(callback)
        .onSegmentDeleted("group");

    assertTrue(poller.retrieveAll());

    assertFalse(evaluator.boolVariation("dependent", target, false, null));
    assertEquals(singletonList("dependent"), notified.get());
  }

  @Test
  void stopsFallbackPollingWhenTheConnectedStreamRecovers() throws Exception {
    InnerClient client = mock(InnerClient.class, CALLS_REAL_METHODS);
    PollingProcessor fallback = new PollingProcessor(connector, repository, 1, client);
    Field field = InnerClient.class.getDeclaredField("pollProcessor");
    field.setAccessible(true);
    field.set(client, fallback);
    doNothing().when(client).onPollerReady();
    AtomicBoolean failing = new AtomicBoolean(true);
    CountDownLatch stopped = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              stopped.countDown();
              return null;
            })
        .when(client)
        .onPollerRefresh();
    when(connector.getFlags()).thenReturn(singletonList(flag("kept", 1)));
    when(connector.getSegments())
        .thenAnswer(
            invocation -> {
              if (failing.get()) throw new ConnectorException("synthetic HTTP 503");
              return emptyList();
            });
    try {
      client.onConnected();
      assertTrue(fallback.isRunning());
      assertFalse(repository.getFlag("kept").isPresent());
      failing.set(false);
      assertTrue(stopped.await(3, SECONDS));
      assertFalse(fallback.isRunning());
      assertTrue(repository.getFlag("kept").isPresent());
    } finally {
      fallback.close();
    }
  }

  @Test
  void retainsNotificationsForChangesAppliedBeforeAStoreFailure() throws Exception {
    AtomicBoolean failing = new AtomicBoolean(true);
    MemoryStorage store =
        new MemoryStorage() {
          @Override
          public void set(String key, Object value) {
            if (key.equals("flags/new") && failing.get()) {
              throw new IllegalStateException("synthetic store failure");
            }
            super.set(key, value);
          }
        };
    StorageRepository stored = new StorageRepository(cache, store, callback, true);
    PollingProcessor storedPoller = new PollingProcessor(connector, stored, 1, pollerCallback);
    stored.setFlag("gone", flag("gone", 1));
    when(connector.getFlags()).thenReturn(singletonList(flag("new", 1)));
    when(connector.getSegments()).thenReturn(emptyList());
    clearInvocations(callback);
    try {
      assertFalse(storedPoller.retrieveAll());
      assertFalse(stored.getFlag("gone").isPresent());
      assertFalse(stored.getFlag("new").isPresent());
      verify(callback).onFlagDeleted("gone");
      verify(callback, never()).onFlagStored("new");
      verify(pollerCallback, never()).onPollerReady();
      failing.set(false);
      assertTrue(storedPoller.retrieveAll());
      assertTrue(stored.getFlag("new").isPresent());
      verify(callback, times(1)).onFlagDeleted("gone");
      verify(callback).onFlagStored("new");
      verify(pollerCallback).onPollerReady();
    } finally {
      storedPoller.close();
    }
  }

  @Test
  void preservesExistingSingleRuleHandlingForStreamedSegments() throws Exception {
    Segment group = segment("group");
    group.setServingRules(singletonList(new GroupServingRule()));
    when(connector.getSegment("group")).thenReturn(group);

    updater.processSegment(new Message("create", "target-segment", "group", 1)).run();

    assertTrue(repository.getSegment("group").isPresent());
    verify(callback).onSegmentStored("group");
  }

  @Test
  void rejectsAMissingFlagResponseWithoutTreatingItAsAnEmptySnapshot() throws Exception {
    repository.setFlag("kept", flag("kept", 1));
    when(connector.getFlags()).thenReturn(null);
    when(connector.getSegments()).thenReturn(emptyList());

    assertFalse(poller.retrieveAll());

    assertTrue(repository.getFlag("kept").isPresent());
    verify(callback, never()).onFlagDeleted("kept");
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

  private static Segment segment(String name) {
    Segment segment = new Segment();
    segment.setIdentifier(name);
    segment.setVersion(1L);
    return segment;
  }

  private static class MemoryStorage implements Storage {
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public void set(String key, Object value) {
      values.put(key, value);
    }

    public Object get(String key) {
      return values.get(key);
    }

    public void delete(String key) {
      values.remove(key);
    }

    public List<String> keys() {
      return new ArrayList<>(values.keySet());
    }

    public void close() {}
  }
}
