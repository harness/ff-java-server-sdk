package io.harness.cf.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Message {
  private final String event;
  private final String domain;
  private final String identifier;
  private final int version;
}
