package io.harness.cf.client.api;

class StreamProcessor {
  public enum Event {
    READY,
    CONNECTED,
    DISCONNECTED,
    CHANGED,
    ERROR,
  }
}
