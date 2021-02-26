package io.harness.cf.client.api.analytics;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.harness.cf.client.dto.Analytics;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
          .expireAfterAccess(60, TimeUnit.SECONDS)
          .build(
              new CacheLoader<Analytics, Integer>() {
                @Override
                public Integer load(Analytics analytics) throws Exception {
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
  public void printCache() {
    log.info(toString());
  }

  @Override
  public String toString() {
    return cache.asMap().toString();
  }
}
