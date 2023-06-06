package io.harness.cf.client.dto;

import lombok.Data;

@Data
public class Claim {
  private final String environment;
  private final String clusterIdentifier;
  private final String accountID;
  private final String environmentIdentifier;
}
