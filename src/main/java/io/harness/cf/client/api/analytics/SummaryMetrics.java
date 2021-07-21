package io.harness.cf.client.api.analytics;

import lombok.*;

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
