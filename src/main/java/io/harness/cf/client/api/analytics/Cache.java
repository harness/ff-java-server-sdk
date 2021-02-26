package io.harness.cf.client.api.analytics;

import io.harness.cf.client.dto.Analytics;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An interface for different cache providers for our analytics service.
 *
 * @author Subir.Adhikari
 * @version 1.0
 */
public interface Cache {
  @Nullable
  Integer get(Analytics a);

  Map<Analytics, Integer> getAll();

  void put(Analytics a, Integer i);

  void resetCache();

  void printCache();
}
