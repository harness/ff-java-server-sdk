package io.harness.cf.client.api;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
  private final int FeatureConfigSize = 10000;
  private final int CacheSize = FeatureConfigSize * 2;

  @Setup
  public void setup() throws Exception {
    TestUtils tu = new TestUtils();
    featureConfigs = tu.CreateBenchmarkData(FeatureConfigSize, 1);
    updatedFeatureConfigs = tu.CreateBenchmarkData(FeatureConfigSize, 2);

    setupRepoWithCurrentOnlyRepository();
    setupRepoWithCurrentAndPreviousRepository();
  }

  @Setup
  public void setupRepoWithCurrentOnlyRepository() throws Exception {

    TestUtils tu = new TestUtils();
    currentOnlyRepo = new StorageRepository(new CaffeineCache(CacheSize), null, false);
    // Initial loading of feature configurations
    loadFlags(currentOnlyRepo, featureConfigs);
  }

  @Setup
  public void setupRepoWithCurrentAndPreviousRepository() throws Exception {

    TestUtils tu = new TestUtils();
    previousAndCurrentRepo = new StorageRepository(new CaffeineCache(CacheSize), null, true);
    // Initial loading of feature configurations
    loadFlags(previousAndCurrentRepo, featureConfigs);
  }

  @Fork(value = 1, warmups = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void BenchmarkLoadFeatureConfigCurrentOnly() {
    // Measure average time taken to load the updated feature configurations while storing only
    // current snapshot
    loadFlags(currentOnlyRepo, updatedFeatureConfigs);
  }

  @Fork(value = 1, warmups = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void BenchmarkLoadFeatureConfigPreviousAndCurrent() {
    // Measure average time taken to load the updated feature configurations while storing current
    // config as well as keeping previous one.
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
