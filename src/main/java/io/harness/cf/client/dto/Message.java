package io.harness.cf.client.dto;

import lombok.Data;

@Data
public class Message {
  private final String event;
  private final String domain;
  private final String identifier;
  private final int version;
}
