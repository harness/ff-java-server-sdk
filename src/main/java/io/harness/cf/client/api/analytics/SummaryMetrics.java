package io.harness.cf.client.api.analytics;

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
public class SummaryMetrics {
  private String featureName;
  private String variationIdentifier;
  private String variationValue;
}
