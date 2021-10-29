package io.harness.cf.client.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.harness.cf.client.common.KeyValueStore;
import java.util.List;
import lombok.NonNull;

public class CaffeineCache implements KeyValueStore {

  private final Cache<String, Object> cache;

  public CaffeineCache(int size) {
    cache = Caffeine.newBuilder().maximumSize(size).build();
  }

  @Override
  public void set(@NonNull String key, @NonNull Object value) {
    cache.put(key, value);
  }

  @Override
  public Object get(@NonNull String key) {
    return cache.getIfPresent(key);
  }

  @Override
  public void del(@NonNull String key) {
    cache.invalidate(key);
  }

  @Override
  public List<String> keys() {
    return null;
  }
}
