package io.harness.cf.client.api.analytics;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnalyticsCacheFactory {
  public static final String CAFFEINE_CACHE = "caffeineCache";
  public static final String GUAVA_CACHE = "guavaCache";

  public static Cache create(String cacheName) {
    if (cacheName.equals(CAFFEINE_CACHE)) {
      log.info("Using Caffeine cache");
      return new CaffeineCache();
    }
    if (cacheName.equals(GUAVA_CACHE)) {
      log.info("Using Guava cache");
      return new GuavaCache();
    }
    return null;
  }
}
