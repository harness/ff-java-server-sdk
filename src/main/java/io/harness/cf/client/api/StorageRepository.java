package io.harness.cf.client.api;

import io.harness.cf.client.common.Cache;
import io.harness.cf.client.common.Operators;
import io.harness.cf.client.common.Storage;
import io.harness.cf.model.Clause;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import io.harness.cf.model.ServingRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import org.apache.commons.collections4.CollectionUtils;

class StorageRepository implements Repository {
  private final Cache cache;
  private Storage store;
  private final RepositoryCallback callback;

  public StorageRepository(@NonNull Cache cache, RepositoryCallback callback) {
    this.cache = cache;
    this.callback = callback;
  }

  public StorageRepository(@NonNull Cache cache, Storage store, RepositoryCallback callback) {
    this(cache, callback);
    this.store = store;
  }

  public Optional<FeatureConfig> getFlag(@NonNull String identifier, boolean cacheable) {
    final String flagKey = formatFlagKey(identifier);
    FeatureConfig flag = (FeatureConfig) cache.get(flagKey);
    if (flag != null) {
      return Optional.of(flag);
    }
    if (this.store != null) {
      flag = (FeatureConfig) store.get(flagKey);
      if (flag != null && cacheable) {
        cache.set(flagKey, flag);
      }
      return Optional.ofNullable(flag);
    }
    return Optional.empty();
  }

  @Override
  public Optional<FeatureConfig> getFlag(@NonNull String identifier) {
    return getFlag(identifier, true);
  }

  public Optional<Segment> getSegment(@NonNull String identifier, boolean cacheable) {
    final String segmentKey = formatSegmentKey(identifier);
    Segment segment = (Segment) cache.get(segmentKey);
    if (segment != null) {
      return Optional.of(segment);
    }
    if (this.store != null) {
      segment = (Segment) store.get(segmentKey);
      if (segment != null && cacheable) {
        cache.set(segmentKey, segment);
      }
      return Optional.ofNullable(segment);
    }
    return Optional.empty();
  }

  @Override
  public Optional<Segment> getSegment(@NonNull String identifier) {
    return getSegment(identifier, true);
  }

  @Override
  public List<String> findFlagsBySegment(@NonNull String segment) {

    List<String> result = new ArrayList<>();
    List<String> keys = this.cache.keys();
    if (store != null) {
      keys = store.keys();
    }
    for (String key : keys) {
      final Optional<FeatureConfig> optionalFeatureConfig = getFlag(key);
      if (!optionalFeatureConfig.isPresent()) {
        continue;
      }
      final FeatureConfig flag = optionalFeatureConfig.get();
      if (CollectionUtils.isEmpty(flag.getRules())) {
        continue;
      }
      for (ServingRule rule : flag.getRules()) {
        for (Clause clause : rule.getClauses()) {
          if (clause.getOp().equals(Operators.SEGMENT_MATCH)
              && clause.getValues().contains(segment)) {
            result.add(flag.getFeature());
          }
        }
      }
    }
    return result;
  }

  @Override
  public void setFlag(@NonNull String identifier, @NonNull FeatureConfig featureConfig) {
    if (isFlagOutdated(identifier, featureConfig)) return;
    final String flagKey = formatFlagKey(identifier);
    if (store != null) {
      store.set(flagKey, featureConfig);
      cache.del(flagKey);
    } else {
      cache.set(flagKey, featureConfig);
    }
    if (callback != null) {
      callback.onFlagStored(identifier);
    }
  }

  @Override
  public void setSegment(@NonNull String identifier, @NonNull Segment segment) {
    if (isSegmentOutdated(identifier, segment)) return;
    final String segmentKey = formatSegmentKey(identifier);
    if (store != null) {
      store.set(segmentKey, segmentKey);
      cache.del(segmentKey);
    } else {
      cache.set(segmentKey, segment);
    }
    if (callback != null) {
      callback.onSegmentStored(identifier);
    }
  }

  @Override
  public void deleteFlag(@NonNull String identifier) {
    final String flagKey = this.formatFlagKey(identifier);
    if (store != null) {
      store.del(flagKey);
    }
    this.cache.del(flagKey);
    if (callback != null) {
      callback.onFlagDeleted(identifier);
    }
  }

  @Override
  public void deleteSegment(@NonNull String identifier) {
    final String segmentKey = this.formatSegmentKey(identifier);
    if (store != null) {
      store.del(segmentKey);
    }
    this.cache.del(segmentKey);
    if (callback != null) {
      callback.onSegmentDeleted(identifier);
    }
  }

  protected boolean isFlagOutdated(
      @NonNull String identifier, @NonNull FeatureConfig newFeatureConfig) {
    final Optional<FeatureConfig> oldFlag = getFlag(identifier, false);
    if (oldFlag.isPresent()) {
      final FeatureConfig flag = oldFlag.get();
      if (flag.getVersion() != null && newFeatureConfig.getVersion() != null)
        return flag.getVersion() >= newFeatureConfig.getVersion();
    }
    return false;
  }

  protected boolean isSegmentOutdated(@NonNull String identifier, @NonNull Segment newSegment) {
    final Optional<Segment> oldSegment = getSegment(identifier, false);
    if (oldSegment.isPresent()) {
      final Segment segment = oldSegment.get();
      if (segment.getVersion() != null && newSegment.getVersion() != null)
        return segment.getVersion() >= newSegment.getVersion();
    }
    return false;
  }

  @NonNull
  protected String formatFlagKey(@NonNull String identifier) {
    return String.format("flags/%s", identifier);
  }

  @NonNull
  protected String formatSegmentKey(@NonNull String identifier) {
    return String.format("segments/%s", identifier);
  }

  @Override
  public void close() {
    if (store != null) {
      store.close();
    }
  }
}