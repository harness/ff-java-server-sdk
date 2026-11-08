package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class StoragePersistenceRegressionTest {
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
  void xmlStoreMustPersistRepositoryFeatureObjects() throws Exception {
    String filename =
        Files.createTempDirectory("harness-xml-audit-").resolve("cache.xml").toString();
    XmlFileMapStore store = new XmlFileMapStore(filename);
    StorageRepository repository =
        new StorageRepository(new CaffeineCache(100), store, null, false);
    repository.setFlag("stored", flag("stored", 1));
    assertDoesNotThrow(
        repository::close, "Configured XML store must persist the SDK's FeatureConfig values");
  }

  @Test
  void reopensTypedFlagsSegmentsAndPreviousVersions() throws Exception {
    String file = Files.createTempDirectory("harness-roundtrip-").resolve("cache.xml").toString();
    StorageRepository repository =
        new StorageRepository(new CaffeineCache(100), new XmlFileMapStore(file), null, true);
    repository.setFlag("roundtrip", flag("roundtrip", 1));
    repository.setFlag("roundtrip", flag("roundtrip", 2));
    Segment segment = new Segment();
    segment.setIdentifier("group");
    segment.setVersion(4L);
    repository.setSegment("group", segment);
    repository.close();
    StorageRepository reopened =
        new StorageRepository(new CaffeineCache(100), new XmlFileMapStore(file), null, true);
    try {
      assertEquals(2L, reopened.getFlag("roundtrip").get().getVersion());
      assertEquals(
          1L, reopened.getCurrentAndPreviousFeatureConfig("roundtrip").get()[0].getVersion());
      assertEquals(4L, reopened.getSegment("group").get().getVersion());
      reopened.deleteFlag("roundtrip");
      reopened.deleteSegment("group");
    } finally {
      reopened.close();
    }
    XmlFileMapStore empty = new XmlFileMapStore(file);
    assertTrue(empty.keys().isEmpty());
    empty.close();
  }

  @Test
  void preservesLegacyStringsAndRejectsUnsupportedValuesBeforeMutation() throws Exception {
    java.nio.file.Path file = Files.createTempDirectory("harness-legacy-").resolve("cache.xml");
    Properties legacy = new Properties();
    legacy.setProperty("custom/key", "legacy value");
    try (java.io.OutputStream out = Files.newOutputStream(file)) {
      legacy.storeToXML(out, "synthetic");
    }
    byte[] original = Files.readAllBytes(file);
    XmlFileMapStore store = new XmlFileMapStore(file.toString());
    assertEquals("legacy value", store.get("custom/key"));
    assertThrows(IllegalArgumentException.class, () -> store.set("unsupported", new Object()));
    assertArrayEquals(original, Files.readAllBytes(file));
    assertNull(store.get("unsupported"));
    store.close();
    XmlFileMapStore reopened = new XmlFileMapStore(file.toString());
    assertEquals("legacy value", reopened.get("custom/key"));
    reopened.close();
  }
}
