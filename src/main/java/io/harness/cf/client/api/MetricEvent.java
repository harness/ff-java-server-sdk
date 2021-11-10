package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Variation;
import lombok.*;

@AllArgsConstructor
@Getter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@Setter
@ToString
class MetricEvent {
  private FeatureConfig featureConfig;
  private Target target;
  private Variation variation;
}
