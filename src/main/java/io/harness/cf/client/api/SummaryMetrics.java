package io.harness.cf.client.api;

import lombok.*;

@AllArgsConstructor
@Getter
@Builder
@EqualsAndHashCode
@NoArgsConstructor
@Setter
@ToString
class SummaryMetrics {
  private String featureName;
  private String variationIdentifier;
  private String variationValue;
}
