package io.harness.cf.compatibility;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.harness.cf.client.api.BaseConfig;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.api.Config;
import io.harness.cf.client.api.Event;
import io.harness.cf.client.api.XmlFileMapStore;
import io.harness.cf.client.common.Cache;
import io.harness.cf.client.common.Storage;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.connector.Service;
import io.harness.cf.client.connector.Updater;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Metrics;
import io.harness.cf.model.Segment;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(15)
class ReleasedConsumerCompatibilityTest {
  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  @SuppressWarnings("deprecation")
  void preservesVariationsAndCustomExtensions(boolean legacyConfig, boolean streaming)
      throws Exception {
    LegacyConnector connector = new LegacyConnector();
    LegacyMap cache = new LegacyMap();
    LegacyMap store = new LegacyMap();
    store.set("application:marker", "preserve");
    CfClient client;
    if (legacyConfig) {
      client = new CfClient();
      client.initialize(
          connector,
          Config.builder()
              .streamEnabled(streaming)
              .analyticsEnabled(false)
              .cache(cache)
              .store(store)
              .enableFeatureSnapshot(true)
              .build());
    } else {
      client =
          new CfClient(
              connector,
              BaseConfig.builder()
                  .streamEnabled(streaming)
                  .analyticsEnabled(false)
                  .cache(cache)
                  .store(store)
                  .enableFeatureSnapshot(true)
                  .build());
    }
    try (CfClient initialized = client) {
      assertTimeoutPreemptively(Duration.ofSeconds(5), initialized::waitForInitialization);
      for (Target target : Arrays.asList(Target.builder().identifier("consumer").build(), null)) {
        cache.values.clear();
        assertTrue(client.boolVariation("simplebool", target, false));
        assertEquals("on-string", client.stringVariation("simplestring", target, "fallback"));
        assertEquals(1.0, client.numberVariation("simplenumber", target, -1));
        assertEquals(
            "on",
            client
                .jsonVariation("simplejson", target, new JsonObject())
                .get("value")
                .getAsString());
        JsonObject fallback = new JsonObject();
        fallback.addProperty("value", "fallback");
        assertFalse(client.boolVariation("missing", target, false));
        assertEquals("fallback", client.stringVariation("missing", target, "fallback"));
        assertEquals(-1.0, client.numberVariation("missing", target, -1));
        assertEquals(fallback, client.jsonVariation("missing", target, fallback));
      }
      assertEquals(4, client.getAllFeatureSnapshots().size());
      assertEquals(1, client.getAllFeatureSnapshots("simplebool").size());
      assertEquals("preserve", store.get("application:marker"));
    }
    assertEquals(1, connector.closes.get());
    assertEquals(1, store.closes.get());
  }

  @Test
  void loadsTheSelectedSdkAndJava8ConsumerBytecode() throws Exception {
    assertEquals(
        Paths.get(System.getProperty("compatibility.sdk.location")).toRealPath(),
        Paths.get(CfClient.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .toRealPath());
    try (DataInputStream consumer =
        new DataInputStream(
            getClass().getResourceAsStream("ReleasedConsumerCompatibilityTest.class"))) {
      assertEquals(0xCAFEBABE, consumer.readInt());
      consumer.readUnsignedShort();
      assertEquals(52, consumer.readUnsignedShort());
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  void preservesManualUpdatesSubscriptionsSnapshotsAndDestroy() throws Exception {
    LegacyConnector connector = new LegacyConnector();
    CfClient client =
        new CfClient(
            connector,
            BaseConfig.builder()
                .streamEnabled(false)
                .analyticsEnabled(false)
                .enableFeatureSnapshot(true)
                .build());
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(5), client::waitForInitialization);
      AtomicInteger notifications = new AtomicInteger();
      Consumer<String> callback = identifier -> notifications.incrementAndGet();
      BlockingQueue<String> completed = new LinkedBlockingQueue<>();
      client.on(Event.CHANGED, callback);
      client.on(Event.CHANGED, completed::add);

      connector.replaceBoolean(2, false);
      client.update(new Message("patch", "flag", "simplebool", 2));
      assertEquals("simplebool", completed.poll(5, TimeUnit.SECONDS));
      assertFalse(client.boolVariation("simplebool", null, true));
      assertEquals(
          Long.valueOf(2), client.getFeatureSnapshot("simplebool").getCurrent().getVersion());
      assertEquals(
          Long.valueOf(1), client.getFeatureSnapshot("simplebool").getPrevious().getVersion());

      client.off(Event.CHANGED, callback);
      connector.replaceBoolean(3, true);
      client.update(new Message("patch", "flag", "simplebool", 3));
      assertEquals("simplebool", completed.poll(5, TimeUnit.SECONDS));
      assertTrue(client.boolVariation("simplebool", null, false));
      assertEquals(1, notifications.get());
      client.off(Event.CHANGED);
      client.off();
    } finally {
      client.destroy();
    }
    assertEquals(1, connector.closes.get());
  }

  @Test
  @SuppressWarnings("deprecation")
  void preservesAnalyticsPayloadsAndPrivateAttributes() throws Exception {
    LegacyConnector connector = new LegacyConnector();
    try (CfClient client =
        new CfClient(
            connector, BaseConfig.builder().streamEnabled(false).analyticsEnabled(true).build())) {
      assertTimeoutPreemptively(Duration.ofSeconds(5), client::waitForInitialization);
      Target target =
          Target.builder()
              .identifier("analytics-consumer")
              .name("Consumer")
              .attribute("public", "visible")
              .attribute("secret", "hidden")
              .privateAttribute("secret")
              .build();
      assertTrue(client.boolVariation("simplebool", target, false));
    }
    assertEquals(
        1,
        connector.metrics.stream()
            .filter(batch -> batch.getMetricsData() != null)
            .flatMap(batch -> batch.getMetricsData().stream())
            .mapToInt(value -> value.getCount())
            .sum());
    String payload = new Gson().toJson(connector.metrics);
    assertTrue(payload.contains("analytics-consumer"));
    assertTrue(payload.contains("visible"));
    assertFalse(payload.contains("hidden"));
    assertFalse(payload.contains("secret"));
  }

  @Test
  void readsAndPreservesLegacyXmlStringEntries(@TempDir Path directory) throws Exception {
    Path file = directory.resolve("legacy.xml");
    Properties legacy = new Properties();
    legacy.setProperty("custom/key", "value & <tag>");
    try (OutputStream output = Files.newOutputStream(file)) {
      legacy.storeToXML(output, "legacy strings");
    }
    try (XmlFileMapStore store = new XmlFileMapStore(file.toString())) {
      assertEquals("value & <tag>", store.get("custom/key"));
      store.set("second", "new value");
    }
    try (XmlFileMapStore reopened = new XmlFileMapStore(file.toString())) {
      assertEquals("value & <tag>", reopened.get("custom/key"));
      assertEquals("new value", reopened.get("second"));
    }
  }

  private static final class LegacyMap implements Cache, Storage {
    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final AtomicInteger closes = new AtomicInteger();

    @Override
    public void set(String key, Object value) {
      values.put(key, value);
    }

    @Override
    public Object get(String key) {
      return values.get(key);
    }

    @Override
    public void delete(String key) {
      values.remove(key);
    }

    @Override
    public List<String> keys() {
      return new ArrayList<>(values.keySet());
    }

    @Override
    public void close() {
      closes.incrementAndGet();
    }
  }

  private static final class LegacyConnector implements Connector {
    private final Map<String, FeatureConfig> flags = new ConcurrentHashMap<>();
    private final AtomicInteger closes = new AtomicInteger();
    private final List<Metrics> metrics = new CopyOnWriteArrayList<>();

    private LegacyConnector() throws Exception {
      try (InputStreamReader reader =
          new InputStreamReader(
              getClass()
                  .getResourceAsStream(
                      "/local-test-cases/basic_bool_string_number_json_variations.json"),
              StandardCharsets.UTF_8)) {
        for (FeatureConfig flag : new Gson().fromJson(reader, FeatureConfig[].class)) {
          flags.put(flag.getFeature(), flag);
        }
      }
    }

    private void replaceBoolean(long version, boolean value) {
      Gson gson = new Gson();
      FeatureConfig replacement =
          gson.fromJson(gson.toJson(flags.get("simplebool")), FeatureConfig.class);
      replacement.setVersion(version);
      replacement.getDefaultServe().setVariation(Boolean.toString(value));
      flags.put("simplebool", replacement);
    }

    @Override
    public String authenticate() {
      return "synthetic-token";
    }

    @Override
    public void setOnUnauthorized(Runnable callback) {}

    @Override
    public List<FeatureConfig> getFlags() {
      return new ArrayList<>(flags.values());
    }

    @Override
    public FeatureConfig getFlag(String identifier) {
      return flags.get(identifier);
    }

    @Override
    public List<Segment> getSegments() {
      return Collections.emptyList();
    }

    @Override
    public Segment getSegment(String identifier) {
      return null;
    }

    @Override
    public void postMetrics(Metrics value) {
      metrics.add(value);
    }

    @Override
    public Service stream(Updater updater) {
      return new Service() {
        @Override
        public void start() {
          updater.onConnected();
          updater.onReady();
        }

        @Override
        public void stop() {}

        @Override
        public void close() {}
      };
    }

    @Override
    public void close() {
      closes.incrementAndGet();
    }

    @Override
    public boolean getShouldFlushAnalyticsOnClose() {
      return true;
    }

    @Override
    public void setIsShuttingDown() {}
  }
}
