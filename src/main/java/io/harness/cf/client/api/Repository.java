package io.harness.cf.client.api;

import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.Collections;
import java.util.Set;
import lombok.NonNull;

public interface Repository extends Query {

  // put values
  void setFlag(@NonNull String identifier, @NonNull FeatureConfig featureConfig);

  void setSegment(@NonNull String identifier, @NonNull Segment segment);

  // remove values
  void deleteFlag(@NonNull String identifier);

  void deleteSegment(@NonNull String identifier);

  /** Identifiers of all flags currently held. */
  default Set<String> flagIdentifiers() {
    return Collections.emptySet();
  }

  /** Identifiers of all segments currently held. */
  default Set<String> segmentIdentifiers() {
    return Collections.emptySet();
  }

  /**
   * Removes cached flags that were present before a full refresh but are absent from it, so
   * deletions missed while the stream was down do not linger. Only identifiers in {@code
   * candidates} are considered, so flags added concurrently by stream events are never removed.
   *
   * @param candidates identifiers known before the refresh started
   * @param current identifiers returned by the refresh
   */
  default void removeStaleFlags(@NonNull Set<String> candidates, @NonNull Set<String> current) {}

  /** Same as {@link #removeStaleFlags(Set, Set)} but for segments. */
  default void removeStaleSegments(@NonNull Set<String> candidates, @NonNull Set<String> current) {}

  void close();
}
