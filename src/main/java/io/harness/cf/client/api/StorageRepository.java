package io.harness.cf.client.api;

import com.google.gson.Gson;
import io.harness.cf.client.common.Cache;
import io.harness.cf.client.common.Storage;
import io.harness.cf.client.common.Utils;
import io.harness.cf.model.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class StorageRepository implements Repository {

  private final Cache cache;
  private Storage store;
  private final RepositoryCallback callback;
  // Stream requests share the read lock so only full refreshes exclude them.
  private final ReadWriteLock updateLock = new ReentrantReadWriteLock(true);
  private List<Runnable> pendingNotifications;

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

  public synchronized Optional<FeatureConfig> getFlag(
      @NonNull String identifier, boolean cacheable) {
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

  public synchronized List<String> getAllFeatureIdentifiers(String prefix) {
    List<String> identifiers = new LinkedList<>();
    Set<String> keys = allKeys();
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

  public synchronized Optional<FeatureConfig[]> getCurrentAndPreviousFeatureConfig(
      @NonNull String identifier) {
    final String flagKey = formatFlagKey(identifier);
    final String pFlagKey = formatPrevFlagKey(identifier);

    FeatureConfig pFlag = (FeatureConfig) cache.get(pFlagKey);
    FeatureConfig cFlag = (FeatureConfig) cache.get(flagKey);

    if (cFlag != null && (pFlag != null || store == null)) {
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

  public synchronized FeatureSnapshot getFeatureSnapshot(@NonNull String identifier) {
    Gson gson = new Gson();
    final String flagKey = formatFlagKey(identifier);
    final String pFlagKey = formatPrevFlagKey(identifier);

    FeatureConfig pFlag = (FeatureConfig) cache.get(pFlagKey);
    FeatureConfig cFlag = (FeatureConfig) cache.get(flagKey);

    if (cFlag != null && (pFlag != null || store == null)) {
      FeatureSnapshot deepCopySnapshot =
          gson.fromJson(gson.toJson(new FeatureSnapshot(cFlag, pFlag)), FeatureSnapshot.class);
      return deepCopySnapshot;
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

      FeatureSnapshot deepCopySnapshot =
          gson.fromJson(gson.toJson(new FeatureSnapshot(cFlag, pFlag)), FeatureSnapshot.class);
      return deepCopySnapshot;
    }
    return null;
  }

  public synchronized Optional<Segment> getSegment(@NonNull String identifier, boolean cacheable) {
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
  public synchronized List<String> findFlagsBySegment(@NonNull String segment) {

    List<String> result = new ArrayList<>();
    Set<String> keys = allKeys();
    for (String key : keys) {
      if (!key.startsWith("flags/")) {
        continue;
      }
      final Optional<FeatureConfig> optionalFeatureConfig =
          getFlag(key.substring("flags/".length()));
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
    if (storeFlag(identifier, featureConfig) && callback != null) {
      publish(() -> callback.onFlagStored(identifier));
    }
  }

  private synchronized boolean storeFlag(String identifier, FeatureConfig featureConfig) {
    if (isFlagOutdated(identifier, featureConfig)) {
      log.debug("Flag {} already exists", identifier);
      return false;
    }

    final String flagKey = formatFlagKey(identifier);
    final Object previousFeatureConfig = store != null ? store.get(flagKey) : cache.get(flagKey);

    if (cachePreviousFeatureConfigVersion && previousFeatureConfig != null) {
      final String previousFlagKey = formatPrevFlagKey(identifier);
      if (store != null) {
        store.set(previousFlagKey, previousFeatureConfig);
        cache.delete(previousFlagKey);
        log.debug("Flag {} successfully stored and cache invalidated", previousFlagKey);
      } else {
        cache.set(previousFlagKey, previousFeatureConfig);
      }
      log.debug("Flag {} successfully stored", previousFlagKey);
    }

    if (store != null) {
      store.set(flagKey, featureConfig);
      cache.delete(flagKey);
    } else {
      cache.set(flagKey, featureConfig);
    }

    log.debug("Flag {} successfully stored", identifier);

    return true;
  }

  @Override
  public void setSegment(@NonNull String identifier, @NonNull Segment segment) {
    if (storeSegment(identifier, segment) && callback != null) {
      publish(() -> callback.onSegmentStored(identifier));
    }
  }

  private synchronized boolean storeSegment(String identifier, Segment segment) {
    if (isSegmentOutdated(identifier, segment)) {
      log.debug("Segment {} already exists", identifier);
      return false;
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
    return true;
  }

  @Override
  public void deleteFlag(@NonNull String identifier) {
    removeFlag(identifier);
    if (callback != null) {
      publish(() -> callback.onFlagDeleted(identifier));
    }
  }

  private synchronized void removeFlag(String identifier) {
    final String flagKey = this.formatFlagKey(identifier);
    final String pflgKey = this.formatPrevFlagKey(identifier);
    if (store != null) {
      store.delete(pflgKey);
      store.delete(flagKey);
      log.debug("Flag {} successfully deleted from store", identifier);
    }
    this.cache.delete(pflgKey);
    this.cache.delete(flagKey);
    log.debug("Flag {} successfully deleted from cache", identifier);
  }

  @Override
  public void deleteSegment(@NonNull String identifier) {
    removeSegment(identifier);
    if (callback != null) {
      publish(() -> callback.onSegmentDeleted(identifier));
    }
  }

  private synchronized void removeSegment(String identifier) {
    final String segmentKey = this.formatSegmentKey(identifier);
    if (store != null) {
      store.delete(segmentKey);
      log.debug("Segment {} successfully deleted from store", identifier);
    }
    this.cache.delete(segmentKey);
    log.debug("Segment {} successfully deleted from cache", identifier);
  }

  Lock updateLock() {
    return updateLock.readLock();
  }

  Lock snapshotLock() {
    return updateLock.writeLock();
  }

  synchronized void replaceSnapshot(ConfigurationSnapshot snapshot, List<Runnable> notifications) {
    Set<String> keys = allKeys();
    Set<String> removedFlags = new LinkedHashSet<>();
    Set<String> removedSegments = new LinkedHashSet<>();
    for (String key : keys) {
      if (key.startsWith("flags/") || key.startsWith("previous/")) {
        String identifier = key.substring(key.indexOf('/') + 1);
        if (!snapshot.flags().containsKey(identifier)) {
          removedFlags.add(identifier);
        }
      } else if (key.startsWith("segments/")) {
        String identifier = key.substring("segments/".length());
        if (!snapshot.segments().containsKey(identifier)) {
          removedSegments.add(identifier);
        }
      }
    }
    collectNotifications(
        () -> {
          removedFlags.forEach(this::deleteFlag);
          removedSegments.forEach(this::deleteSegment);
          snapshot.segments().forEach(this::setSegment);
          snapshot.flags().forEach(this::setFlag);
        },
        notifications);
  }

  synchronized void collectNotifications(Runnable mutation, List<Runnable> notifications) {
    pendingNotifications = notifications;
    try {
      mutation.run();
    } finally {
      pendingNotifications = null;
    }
  }

  static void notifyCallbacks(List<Runnable> notifications) {
    for (Runnable notification : notifications) {
      try {
        notification.run();
      } catch (RuntimeException ex) {
        log.warn("Repository notification failed", ex);
      }
    }
  }

  private Set<String> allKeys() {
    Set<String> keys = new LinkedHashSet<>(cache.keys());
    if (store != null) {
      keys.addAll(store.keys());
    }
    return keys;
  }

  private void publish(Runnable notification) {
    synchronized (this) {
      if (pendingNotifications != null) {
        pendingNotifications.add(notification);
        return;
      }
    }
    notification.run();
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

  static void sortSegmentServingRules(Segment segment) {
    if (segment.getServingRules() != null && segment.getServingRules().size() > 1) {
      List<GroupServingRule> rules = new ArrayList<>(segment.getServingRules());
      rules.sort(Comparator.comparing(GroupServingRule::getPriority));
      segment.setServingRules(rules);
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
