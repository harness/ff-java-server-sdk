package io.harness.cf.client.api;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import java.util.List;
import org.openjdk.jmh.annotations.*;

/*
 How to run it.
 ./gradlew clean build
 ./gradlew jmh -Pjmh.include=BenchmarkClassName.BenchmarkMethodName

 e.g.
 ./gradlew jmh -Pjmh.include=StoreRepositoryBenchmark.BenchmarkLoadFeatureConfigCurrentOnly
 ./gradlew jmh -Pjmh.include=StoreRepositoryBenchmark.BenchmarkLoadFeatureConfigPreviousAndCurrent
*/

@State(Scope.Thread)
public class StoreRepositoryBenchmark {

  private Repository currentOnlyRepo;
  private Repository previousAndCurrentRepo;
  private List<FeatureConfig> featureConfigs;
  private List<FeatureConfig> updatedFeatureConfigs;
  private final Gson gson = new Gson();

  @Setup
  public void setup() throws Exception {
    setupRepoWithCurrentOnlyRepository();
    setupRepoWithCurrentAndPreviousRepository();
  }

  @Setup
  public void setupRepoWithCurrentOnlyRepository() throws Exception {

    TestUtils tu = new TestUtils();
    currentOnlyRepo = new StorageRepository(new CaffeineCache(10000), null, false);

    // Define the sample size for benchmarking
    int sampleSize = 1000;

    // Create benchmark data
    featureConfigs = tu.CreateBenchmarkData(sampleSize, 1);
    updatedFeatureConfigs = tu.CreateBenchmarkData(sampleSize, 2);

    // Ensure data is created correctly
    if (featureConfigs == null || featureConfigs.size() != sampleSize) {
      throw new IllegalStateException("Feature configs setup failed.");
    }
    if (updatedFeatureConfigs == null || updatedFeatureConfigs.size() != sampleSize) {
      throw new IllegalStateException("Updated feature configs setup failed.");
    }

    // Initial loading of feature configurations
    loadFlags(currentOnlyRepo, featureConfigs);
  }

  @Setup
  public void setupRepoWithCurrentAndPreviousRepository() throws Exception {

    TestUtils tu = new TestUtils();
    previousAndCurrentRepo = new StorageRepository(new CaffeineCache(10000), null, true);

    // Define the sample size for benchmarking
    int sampleSize = 1000;

    // Create benchmark data
    featureConfigs = tu.CreateBenchmarkData(sampleSize, 1);
    updatedFeatureConfigs = tu.CreateBenchmarkData(sampleSize, 2);

    // Ensure data is created correctly
    if (featureConfigs == null || featureConfigs.size() != sampleSize) {
      throw new IllegalStateException("Feature configs setup failed.");
    }
    if (updatedFeatureConfigs == null || updatedFeatureConfigs.size() != sampleSize) {
      throw new IllegalStateException("Updated feature configs setup failed.");
    }

    // Initial loading of feature configurations
    loadFlags(previousAndCurrentRepo, featureConfigs);
  }

  @Fork(value = 1, warmups = 1)
  @Benchmark
  public void BenchmarkLoadFeatureConfigCurrentOnly() {
    // Measure time taken to load the updated feature configurations while storing only current
    // snapshot
    loadFlags(currentOnlyRepo, updatedFeatureConfigs);
  }

  
  @Fork(value = 1, warmups = 1)
  @Benchmark
  public void BenchmarkLoadFeatureConfigPreviousAndCurrent() {
    // Measure time taken to load the updated feature configurations while storing only current
    // snapshot as well as keeping previous snapshot.
    loadFlags(previousAndCurrentRepo, updatedFeatureConfigs);
  }

  private void loadFlags(Repository repository, List<FeatureConfig> flags) {
    if (flags != null) {
      for (FeatureConfig nextFlag : flags) {
        repository.setFlag(nextFlag.getFeature(), nextFlag);
      }
    }
  }
}
