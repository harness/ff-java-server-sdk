package io.harness.cf.client.api.analytics;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.harness.cf.client.dto.Analytics;
import java.util.Map;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A custom class for implementing the interface methods of cache interface. It uses Guava cache as
 * the cache service provider.
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
@Slf4j
public class GuavaCache implements Cache {
  LoadingCache<Analytics, Integer> cache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .build(
              new CacheLoader<Analytics, Integer>() {
                @Override
                @NonNull
                public Integer load(@NonNull Analytics analytics) {
                  return 0;
                }
              });

  @SneakyThrows
  @Override
  public @Nullable Integer get(Analytics a) {
    return cache.get(a);
  }

  @Override
  public Map<Analytics, Integer> getAll() {
    return cache.asMap();
  }

  @Override
  public void put(Analytics a, Integer i) {
    cache.put(a, i);
  }

  @Override
  public void resetCache() {
    cache.invalidateAll();
  }

  @Override
  public String toString() {
    return cache.asMap().toString();
  }
}
