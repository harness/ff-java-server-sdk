package io.harness.cf.client.api;

import io.harness.cf.client.common.Cache;
import io.harness.cf.client.common.Storage;
import io.harness.cf.client.common.Utils;
import io.harness.cf.model.*;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class StorageRepository implements Repository {

  private final Cache cache;
  private Storage store;
  private final RepositoryCallback callback;

  private final boolean cachePreviousFeatureConfigVersion;

  public StorageRepository(
      @NonNull Cache cache,
      RepositoryCallback callback,
      boolean cachePreviousFeatureConfigVersion) {
    this.cache = cache;
    this.callback = callback;
    this.cachePreviousFeatureConfigVersion = cachePreviousFeatureConfigVersion;
  }

  public StorageRepository(
      @NonNull Cache cache,
      Storage store,
      RepositoryCallback callback,
      boolean cachePreviousFeatureConfigVersion) {
    this(cache, callback, cachePreviousFeatureConfigVersion);
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

  public List<String> getAllFeatureIdentifiers(String prefix) {
    List<String> identifiers = new LinkedList<>();
    List<String> keys = cache.keys();
    String flagPrefix = "flags/";
    for (String key : keys) {
      if (key.startsWith(flagPrefix)) {
        // Strip the flag prefix
        String strippedKey = key.substring(flagPrefix.length());
        // If prefix is empty, add all stripped keys, otherwise check for prefix match
        if (prefix.isEmpty() || strippedKey.startsWith(prefix)) {
          identifiers.add(strippedKey);
        }
      }
    }
    return identifiers;
  }

  public Optional<FeatureConfig[]> getCurrentAndPreviousFeatureConfig(@NonNull String identifier) {
    final String flagKey = formatFlagKey(identifier);
    final String pFlagKey = formatPrevFlagKey(identifier);

    FeatureConfig pFlag = (FeatureConfig) cache.get(pFlagKey);
    FeatureConfig cFlag = (FeatureConfig) cache.get(flagKey);

    if (cFlag != null) {
      return Optional.of(new FeatureConfig[] {pFlag, cFlag});
    }
    // if we don't have it in cache we check the file
    if (this.store != null) {
      pFlag = (FeatureConfig) store.get(pFlagKey);
      cFlag = (FeatureConfig) store.get(flagKey);
      if (pFlag != null) {
        cache.set(pFlagKey, pFlag);
      }
      if (cFlag != null) {
        cache.set(flagKey, cFlag);
      }
      return Optional.of(new FeatureConfig[] {pFlag, cFlag});
    }
    return Optional.empty();
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
      log.debug("Store is available, load all keys");
      keys = store.keys();
    }
    for (String key : keys) {
      final Optional<FeatureConfig> optionalFeatureConfig = getFlag(key);
      if (!optionalFeatureConfig.isPresent()) {
        log.debug("Flag not found {}, continue...", key);
        continue;
      }
      final FeatureConfig flag = optionalFeatureConfig.get();
      if (Utils.isEmpty(flag.getRules())) {
        log.debug("Flag {} doesn't contain any rule, continue...", key);
        continue;
      }
      for (ServingRule rule : flag.getRules()) {
        for (Clause clause : rule.getClauses()) {
          if (clause.getOp().equals(Operators.SEGMENT_MATCH)
              && clause.getValues().contains(segment)) {
            log.debug("Flag {} evaluated in segments", flag.getFeature());
            result.add(flag.getFeature());
          }
        }
      }
    }
    return result;
  }

  @Override
  public void setFlag(@NonNull String identifier, @NonNull FeatureConfig featureConfig) {
    if (isFlagOutdated(identifier, featureConfig)) {
      log.debug("Flag {} already exists", identifier);
      return;
    }

    final String flagKey = formatFlagKey(identifier);
    final Object previousFeatureConfig = store != null ? store.get(flagKey) : cache.get(flagKey);

    if (cachePreviousFeatureConfigVersion && previousFeatureConfig != null) {
      final String previousFlagKey = formatPrevFlagKey(identifier);
      if (store != null) {
        store.set(previousFlagKey, previousFeatureConfig);
        cache.delete(previousFlagKey);
      } else {
        cache.set(previousFlagKey, previousFeatureConfig);
      }
      log.debug("Flag {} successfully stored and cache invalidated", previousFlagKey);
    }

    if (store != null) {
      store.set(flagKey, featureConfig);
      cache.delete(flagKey);
    } else {
      cache.set(flagKey, featureConfig);
    }

    log.debug("Flag {} successfully stored", identifier);

    if (callback != null) {
      callback.onFlagStored(identifier);
    }
  }

  @Override
  public void setSegment(@NonNull String identifier, @NonNull Segment segment) {
    if (isSegmentOutdated(identifier, segment)) {
      log.debug("Segment {} already exists", identifier);
      return;
    }

    // Sort the serving rules before storing the segment
    sortSegmentServingRules(segment);

    final String segmentKey = formatSegmentKey(identifier);
    if (store != null) {
      store.set(segmentKey, segment);
      cache.delete(segmentKey);
      log.debug("Segment {} successfully stored and cache invalidated", identifier);
    } else {
      cache.set(segmentKey, segment);
      log.debug("Segment {} successfully cached", identifier);
    }
    if (callback != null) {
      callback.onSegmentStored(identifier);
    }
  }

  @Override
  public void deleteFlag(@NonNull String identifier) {
    final String flagKey = this.formatFlagKey(identifier);
    final String pflgKey = this.formatPrevFlagKey(identifier);
    if (store != null) {
      if (cachePreviousFeatureConfigVersion) {
        store.delete(pflgKey);
      }
      store.delete(flagKey);
      log.debug("Flag {} successfully deleted from store", identifier);
    }
    if (cachePreviousFeatureConfigVersion) {
      this.cache.delete(pflgKey);
    }
    this.cache.delete(flagKey);
    log.debug("Flag {} successfully deleted from cache", identifier);
    if (callback != null) {
      callback.onFlagDeleted(identifier);
    }
  }

  @Override
  public void deleteSegment(@NonNull String identifier) {
    final String segmentKey = this.formatSegmentKey(identifier);
    if (store != null) {
      store.delete(segmentKey);
      log.debug("Segment {} successfully deleted from store", identifier);
    }
    this.cache.delete(segmentKey);
    log.debug("Segment {} successfully deleted from cache", identifier);
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
    log.debug("Flag is outdated {}", identifier);
    return false;
  }

  protected boolean isSegmentOutdated(@NonNull String identifier, @NonNull Segment newSegment) {
    final Optional<Segment> oldSegment = getSegment(identifier, false);
    if (oldSegment.isPresent()) {
      final Segment segment = oldSegment.get();
      if (segment.getVersion() != null && newSegment.getVersion() != null)
        return segment.getVersion() >= newSegment.getVersion();
    }
    log.debug("Segment is outdated {}", identifier);
    return false;
  }

  private void sortSegmentServingRules(Segment segment) {
    if (segment.getServingRules() != null && segment.getServingRules().size() > 1) {
      segment.getServingRules().sort(Comparator.comparing(GroupServingRule::getPriority));
    }
  }

  @NonNull
  protected String formatFlagKey(@NonNull String identifier) {
    return String.format("flags/%s", identifier);
  }

  protected String formatPrevFlagKey(@NonNull String identifier) {
    return String.format("previous/%s", identifier);
  }

  @NonNull
  protected String formatSegmentKey(@NonNull String identifier) {
    return String.format("segments/%s", identifier);
  }

  @Override
  public void close() {
    if (store != null) {
      store.close();
      log.debug("store closed");
    }
  }
}
