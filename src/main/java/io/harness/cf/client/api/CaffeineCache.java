package io.harness.cf.client.api;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.harness.cf.client.common.Cache;
import io.harness.cf.client.logger.LogUtil;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CaffeineCache implements Cache {

  private final com.github.benmanes.caffeine.cache.Cache<String, Object> cache;

  static {
    LogUtil.setSystemProps();
  }

  public CaffeineCache(int size) {
    cache = Caffeine.newBuilder().maximumSize(size).build();
    log.trace("CaffeineCache initialized with size {}", size);
  }

  @Override
  public void set(@NonNull String key, @NonNull Object value) {
    cache.put(key, value);
    log.trace("New value in the cache with key '{}' and value '{}'", key, value);
  }

  @Override
  @Nullable
  public Object get(@NonNull String key) {
    Object value = cache.getIfPresent(key);
    if (value != null) {
      log.trace("Key '{}' found in cache with value '{}'", key, value);
    } else {
      log.trace("Key '{}' not found in cache", key);
    }
    return value;
  }

  @Override
  public void delete(@NonNull String key) {
    cache.invalidate(key);
    log.trace("Key {} removed from cache", key);
  }

  @Override
  public List<String> keys() {
    List<String> keys = new ArrayList<>(cache.asMap().keySet());
    log.trace("Keys in cache {}", keys);
    return keys;
  }
}
