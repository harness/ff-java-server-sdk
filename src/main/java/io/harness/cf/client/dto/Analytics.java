package io.harness.cf.client.dto;

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
public class Analytics {
  private FeatureConfig featureConfig;
  private Target target;
  private Variation variation;
  private EventType eventType = EventType.METRICS;
}
