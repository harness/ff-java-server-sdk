package io.harness.cf.client.connector;

public interface Service {
  void start() throws InterruptedException, ConnectorException;

  void stop() throws InterruptedException;

  void close() throws InterruptedException;
}
