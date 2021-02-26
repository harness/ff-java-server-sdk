package io.harness.cf.client.dto;

import io.harness.cf.model.FeatureConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
  private Object variation;
  private EventType eventType = EventType.METRICS;
}
