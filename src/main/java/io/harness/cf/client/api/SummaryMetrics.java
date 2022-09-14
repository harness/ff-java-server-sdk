package io.harness.cf.client.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Data
class SummaryMetrics {
  private String featureName;
  private String variationIdentifier;
  private String variationValue;
  private String targetIdentifier;
}
