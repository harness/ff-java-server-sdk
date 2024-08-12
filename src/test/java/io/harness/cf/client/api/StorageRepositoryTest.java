package io.harness.cf.client.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.NonNull;
import org.junit.jupiter.api.Test;

class StorageRepositoryTest {
  private final Gson gson = new Gson();

  @Test
  void shouldInitialiseRepo() {
    final Repository repository = new StorageRepository(new CaffeineCache(10000), null, false);
    assertInstanceOf(StorageRepository.class, repository);
  }

  @Test
  void shouldStoreCurrentConfig() throws Exception {
    final Repository repository = new StorageRepository(new CaffeineCache(10000), null, false);
    assertInstanceOf(StorageRepository.class, repository);

    FeatureConfig featureConfig = GetFeatureConfigFromFile();
    FeatureConfig featureConfigUpdated = GetUpdatedFeatureConfigFromFile();

    assertNotNull(featureConfig);
    assertNotNull(featureConfigUpdated);

    loadFlags(repository, makeFeatureList(featureConfig));
    loadFlags(repository, makeFeatureList(featureConfigUpdated));

    Optional<FeatureConfig[]> res =
        repository.getCurrentAndPreviousFeatureConfig(featureConfigUpdated.getFeature());
    FeatureConfig[] resFc = res.get();

    FeatureConfig previous = resFc[0];
    FeatureConfig current = resFc[1];

    // check if previous version is null
    assertNull(previous);
    assertNotNull(current);

    // check if the current version is correct
    assertEquals(current.getVersion(), new Long(2));
  }

  @Test
  void shouldStoreCurrentConfigWithFileStore() throws Exception {

    File file = File.createTempFile(FileMapStoreTest.class.getSimpleName(), ".tmp");
    file.deleteOnExit();

    XmlFileMapStore store = new XmlFileMapStore(file.getAbsolutePath());

    final Repository repository =
        new StorageRepository(new CaffeineCache(10000), store, null, false);
    assertInstanceOf(StorageRepository.class, repository);

    FeatureConfig featureConfig = GetFeatureConfigFromFile();
    FeatureConfig featureConfigUpdated = GetUpdatedFeatureConfigFromFile();

    assertNotNull(featureConfig);
    assertNotNull(featureConfigUpdated);

    loadFlags(repository, makeFeatureList(featureConfig));
    loadFlags(repository, makeFeatureList(featureConfigUpdated));

    Optional<FeatureConfig[]> res =
        repository.getCurrentAndPreviousFeatureConfig(featureConfigUpdated.getFeature());
    FeatureConfig[] resFc = res.get();

    FeatureConfig previous = resFc[0];
    FeatureConfig current = resFc[1];

    // check if previous version is null
    assertNull(previous);
    assertNotNull(current);

    // check if the current version is correct
    assertEquals(current.getVersion(), new Long(2));
  }

  @Test
  void shouldStorePreviousAndCurrentConfigWithFileStore() throws Exception {

    File file = File.createTempFile(FileMapStoreTest.class.getSimpleName(), ".tmp");
    file.deleteOnExit();

    XmlFileMapStore store = new XmlFileMapStore(file.getAbsolutePath());

    final Repository repository =
        new StorageRepository(new CaffeineCache(10000), store, null, true);
    assertInstanceOf(StorageRepository.class, repository);

    FeatureConfig featureConfig = GetFeatureConfigFromFile();
    FeatureConfig featureConfigUpdated = GetUpdatedFeatureConfigFromFile();

    assertNotNull(featureConfig);
    assertNotNull(featureConfigUpdated);

    loadFlags(repository, makeFeatureList(featureConfig));
    loadFlags(repository, makeFeatureList(featureConfigUpdated));

    Optional<FeatureConfig[]> res =
        repository.getCurrentAndPreviousFeatureConfig(featureConfigUpdated.getFeature());
    FeatureConfig[] resFc = res.get();

    FeatureConfig previous = resFc[0];
    FeatureConfig current = resFc[1];

    // check if previous version is null
    assertNotNull(previous);
    assertNotNull(current);

    // check if the current version is correct
    assertEquals(previous.getVersion(), new Long(1));
    assertEquals(current.getVersion(), new Long(2));
  }

  @Test
  void shouldStorePreviousAndCurrentConfig() throws Exception {
    final Repository repository = new StorageRepository(new CaffeineCache(10000), null, true);
    assertInstanceOf(StorageRepository.class, repository);

    FeatureConfig featureConfig = GetFeatureConfigFromFile();
    FeatureConfig featureConfigUpdated = GetUpdatedFeatureConfigFromFile();

    assertNotNull(featureConfig);
    assertNotNull(featureConfigUpdated);

    loadFlags(repository, makeFeatureList(featureConfig));
    loadFlags(repository, makeFeatureList(featureConfigUpdated));

    Optional<FeatureConfig[]> res =
        repository.getCurrentAndPreviousFeatureConfig(featureConfigUpdated.getFeature());
    FeatureConfig[] resFc = res.get();

    FeatureConfig previous = resFc[0];
    FeatureConfig current = resFc[1];

    // check if previous version is null
    assertNotNull(previous);
    assertNotNull(current);

    // check if the current version is correct
    assertEquals(previous.getVersion(), new Long(1));
    assertEquals(current.getVersion(), new Long(2));
  }

  @Test
  void shouldDeletePreviousAndCurrentConfig() throws Exception {
    final Repository repository = new StorageRepository(new CaffeineCache(10000), null, true);
    assertInstanceOf(StorageRepository.class, repository);

    FeatureConfig featureConfig = GetFeatureConfigFromFile();
    FeatureConfig featureConfigUpdated = GetUpdatedFeatureConfigFromFile();

    assertNotNull(featureConfig);
    assertNotNull(featureConfigUpdated);

    loadFlags(repository, makeFeatureList(featureConfig));
    loadFlags(repository, makeFeatureList(featureConfigUpdated));

    String featureIdentifier = featureConfig.getFeature();

    Optional<FeatureConfig[]> res =
        repository.getCurrentAndPreviousFeatureConfig(featureIdentifier);
    FeatureConfig[] resFc = res.get();

    FeatureConfig previous = resFc[0];
    FeatureConfig current = resFc[1];

    // check if previous version is null
    assertNotNull(previous);
    assertNotNull(current);

    // check if the current version is correct
    assertEquals(previous.getVersion(), new Long(1));
    assertEquals(current.getVersion(), new Long(2));

    // delete config
    repository.deleteFlag(featureIdentifier);
    Optional<FeatureConfig[]> result =
        repository.getCurrentAndPreviousFeatureConfig(featureIdentifier);

    assertFalse(result.isPresent(), "The Optional should be empty");
  }

  @Test
  void shouldListAllKeysTest() throws Exception {
    final Repository repository = new StorageRepository(new CaffeineCache(10000), null, true);
    assertInstanceOf(StorageRepository.class, repository);

    FeatureConfig featureConfig = GetFeatureConfigFromFile();
    FeatureConfig featureConfigUpdated = GetUpdatedFeatureConfigFromFile();

    assertNotNull(featureConfig);
    assertNotNull(featureConfigUpdated);

    loadFlags(repository, makeFeatureList(featureConfig));
    loadFlags(repository, makeFeatureList(featureConfigUpdated));

    List<String> keys = repository.getAllFeatureIdentifiers("");
    assertEquals(keys.size(), 1);
    assertEquals(keys.get(0), featureConfigUpdated.getFeature());
  }

  private void loadFlags(Repository repository, List<FeatureConfig> flags) {
    if (flags != null) {
      for (FeatureConfig nextFlag : flags) {
        repository.setFlag(nextFlag.getFeature(), nextFlag);
      }
    }
  }

  @NonNull
  private String read(@NonNull final String path) {

    final StringBuilder builder = new StringBuilder();
    try (final Stream<String> stream = Files.lines(Paths.get(path), StandardCharsets.UTF_8)) {
      stream.forEach(s -> builder.append(s).append("\n"));
    } catch (IOException e) {
      fail(e.getMessage());
    }
    return builder.toString();
  }

  private List<FeatureConfig> makeFeatureList(FeatureConfig fc) {
    List<FeatureConfig> fg = new LinkedList<>();
    fg.add(fc);
    return fg;
  }

  private FeatureConfig GetUpdatedFeatureConfigFromFile() throws Exception {
    FeatureConfig fc = GetFeatureConfigFromFile();
    fc.setVersion(new Long(2));
    return fc;
  }

  // get the flags and populate it.
  private FeatureConfig GetFeatureConfigFromFile() throws Exception {
    try {
      String relativePath =
          "./src/test/resources/local-test-cases/basic_bool_string_for_repository.json";
      // Resolve the absolute path
      String filePath = new File(relativePath).getCanonicalPath();
      // Read the content of the file into a String
      String jsonString = new String(Files.readAllBytes(Paths.get(filePath)));
      final FeatureConfig featureConfig = gson.fromJson(jsonString, FeatureConfig.class);
      return featureConfig;

    } catch (IOException e) {
      // Handle exceptions like file not found or read errors
      e.printStackTrace();
    }
    return null;
  }

  private List<FeatureConfig> createBenchmarkData(int flagNumber, int version) throws Exception {
    FeatureConfig fg = GetFeatureConfigFromFile();
    List<FeatureConfig> list = new LinkedList<FeatureConfig>();
    for (int i = 1; i <= flagNumber; i++) {
      FeatureConfig f = fg;
      f.setFeature("simpleBool" + i);
      f.setVersion(new Long(version));
      list.add(f);
    }
    //    System.out.println(list);
    return list;
  }
}
