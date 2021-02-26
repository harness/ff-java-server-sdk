package io.harness.cf.client.api.analytics;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.harness.cf.client.dto.Analytics;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A custom class for implementing the interface methods of cache interface. It uses Caffein cache
 * as the cache service provider.
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
@Slf4j
public class CaffeineCache implements Cache {
  com.github.benmanes.caffeine.cache.Cache<Analytics, Integer> cache =
      Caffeine.newBuilder().maximumSize(10000).build();

  @Override
  public @Nullable Integer get(Analytics result) {
    return cache.getIfPresent(result);
  }

  @Override
  public @NonNull ConcurrentMap<@NonNull Analytics, @NonNull Integer> getAll() {
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
