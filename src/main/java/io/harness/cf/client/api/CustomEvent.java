package io.harness.cf.client.api;

import lombok.Getter;

public class CustomEvent<T extends Enum<T>> {

  @Getter private final Enum<T> event;
  @Getter private Object value;

  public CustomEvent(Enum<T> event) {
    this.event = event;
  }

  public CustomEvent(Enum<T> event, Object value) {
    this(event);
    this.value = value;
  }
}
