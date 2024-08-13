package io.harness.cf.client.api;

import com.google.gson.Gson;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.FeatureSnapshot;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

/*
 How to run it.
 ./gradlew clean build
 ./gradlew jmh

 some results:
  Benchmark                                                              Mode  Cnt  Score   Error  Units
  StoreRepositoryBenchmark.BenchmarkLoadFeatureConfigCurrentOnly         avgt    5  1.160 ± 0.059  ms/op
  StoreRepositoryBenchmark.BenchmarkLoadFeatureConfigPreviousAndCurrent  avgt    5  1.224 ± 0.025  ms/op

*/

@State(Scope.Thread)
public class StoreRepositoryBenchmark {

  private Repository setCurrentOnlyRepository;
  private Repository setCurrentAndPreviousRepository;

  private Repository getSnapshotCurrentOnlyRepository;
  private Repository getSnapshotCurrentAndPreviousRepository;
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
    setupRepoWithCurrentOnlyRepositoryForGetSnapshot();
    setupRepoWithCurrentAndPreviousRepositoryForGetSnapshot();
  }

  @Setup
  public void setupRepoWithCurrentOnlyRepository() throws Exception {

    TestUtils tu = new TestUtils();
    setCurrentOnlyRepository = new StorageRepository(new CaffeineCache(CacheSize), null, false);
    // Initial loading of feature configurations
    loadFlags(setCurrentOnlyRepository, featureConfigs);
  }

  @Setup
  public void setupRepoWithCurrentAndPreviousRepository() throws Exception {

    TestUtils tu = new TestUtils();
    setCurrentAndPreviousRepository =
        new StorageRepository(new CaffeineCache(CacheSize), null, true);
    // Initial loading of feature configurations
    loadFlags(setCurrentAndPreviousRepository, featureConfigs);
  }

  @Setup
  public void setupRepoWithCurrentOnlyRepositoryForGetSnapshot() throws Exception {

    TestUtils tu = new TestUtils();
    getSnapshotCurrentOnlyRepository =
        new StorageRepository(new CaffeineCache(CacheSize), null, false);
    // Initial loading of feature configurations
    loadFlags(getSnapshotCurrentOnlyRepository, featureConfigs);
    loadFlags(getSnapshotCurrentOnlyRepository, updatedFeatureConfigs);
  }

  @Setup
  public void setupRepoWithCurrentAndPreviousRepositoryForGetSnapshot() throws Exception {
    TestUtils tu = new TestUtils();
    getSnapshotCurrentAndPreviousRepository =
        new StorageRepository(new CaffeineCache(CacheSize), null, true);
    // Initial loading of feature configurations
    loadFlags(getSnapshotCurrentAndPreviousRepository, featureConfigs);
    loadFlags(getSnapshotCurrentAndPreviousRepository, updatedFeatureConfigs);
  }

  @Fork(value = 1, warmups = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void BenchmarkLoadFeatureConfigCurrentOnly() {
    // Measure average time taken to load the updated feature configurations while storing only
    // current snapshot
    loadFlags(setCurrentOnlyRepository, updatedFeatureConfigs);
  }

  @Fork(value = 1, warmups = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void BenchmarkLoadFeatureConfigPreviousAndCurrent() {
    // Measure average time taken to load the updated feature configurations while storing current
    // config as well as keeping previous one.
    loadFlags(setCurrentAndPreviousRepository, updatedFeatureConfigs);
  }

  @Fork(value = 1, warmups = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void BenchmarkGetFeatureSnapshotsCurrentOnly() {
    // Measures time taken to get snapshot
    List<FeatureSnapshot> snapshots = getFeatureSnapshots(getSnapshotCurrentOnlyRepository);
    if (snapshots == null) {
      throw new IllegalStateException("Snapshots are null");
    }
    if (snapshots.size() != FeatureConfigSize) {
      throw new IllegalStateException("Snapshots not equal");
    }

    for (int i = 0; i < FeatureConfigSize; i++) {
      FeatureSnapshot fs = snapshots.get(i);
      if (fs.getPrevious() != null) {
        throw new IllegalStateException("Snapshots contains previous");
      }
      if (fs.getCurrent() == null) {
        throw new IllegalStateException("Snapshots does not contain current");
      }
    }
  }

  @Fork(value = 1, warmups = 1)
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Benchmark
  @BenchmarkMode(Mode.AverageTime)
  public void BenchmarkGetFeatureSnapshotsPreviousAndCurrent() {
    // Measures time taken to get snapshot
    List<FeatureSnapshot> snapshots = getFeatureSnapshots(getSnapshotCurrentAndPreviousRepository);
    if (snapshots == null) {
      throw new IllegalStateException("Snapshots are null");
    }
    if (snapshots.size() != FeatureConfigSize) {
      throw new IllegalStateException("Snapshots not equal");
    }

    for (int i = 0; i < FeatureConfigSize; i++) {
      FeatureSnapshot fs = snapshots.get(i);
      if (fs.getPrevious() == null) {
        throw new IllegalStateException("Snapshots does not contain previous");
      }
      if (fs.getCurrent() == null) {
        throw new IllegalStateException("Snapshots does not contain current");
      }
    }
  }

  private List<FeatureSnapshot> getFeatureSnapshots(Repository repository) {
    List<String> identifiers = repository.getAllFeatureIdentifiers("");
    List<FeatureSnapshot> snapshots = new LinkedList<>();
    for (String identifier : identifiers) {
      FeatureSnapshot snapshot = repository.getFeatureSnapshot(identifier);
      snapshots.add(snapshot);
    }
    return snapshots;
  }

  private void loadFlags(Repository repository, List<FeatureConfig> flags) {
    if (flags != null) {
      for (FeatureConfig nextFlag : flags) {
        repository.setFlag(nextFlag.getFeature(), nextFlag);
      }
    }
  }
}
