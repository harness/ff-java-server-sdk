package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.Variation;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Builder
@Data
class MetricEvent {
  private String featureName;
  private Target target;
  private Variation variation;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MetricEvent that = (MetricEvent) o;
    return featureName.equals(that.featureName)
        && target.equals(that.target)
        && variation.equals(that.variation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(featureName, target, variation);
  }
}
