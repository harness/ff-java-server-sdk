package io.harness.cf.client.api;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ConfigurationSnapshot {
  private final Map<String, FeatureConfig> flags;
  private final Map<String, Segment> segments;

  ConfigurationSnapshot(List<FeatureConfig> flags, List<Segment> segments) {
    this.flags = index(flags, FeatureConfig::getFeature, "flag");
    this.segments = index(segments, Segment::getIdentifier, "segment");
    this.segments.values().forEach(StorageRepository::sortSegmentServingRules);
  }

  Map<String, FeatureConfig> flags() {
    return flags;
  }

  Map<String, Segment> segments() {
    return segments;
  }

  private static <T> Map<String, T> index(
      List<T> values, Function<T, String> identifier, String kind) {
    if (values == null) {
      throw new IllegalArgumentException("Missing " + kind + " snapshot");
    }
    Map<String, T> result = new LinkedHashMap<>();
    for (T value : values) {
      String key = value == null ? null : identifier.apply(value);
      if (key == null || key.trim().isEmpty() || result.containsKey(key)) {
        throw new IllegalArgumentException(
            "Invalid or duplicate identifier in " + kind + " snapshot");
      }
      result.put(key, value);
    }
    return Collections.unmodifiableMap(result);
  }
}
