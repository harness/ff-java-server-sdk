package io.harness.cf.client.api;

public class StreamProcessor {
  public enum Event {
    READY,
    CONNECTED,
    DISCONNECTED,
    CHANGED,
    ERROR,
  }
}
