package io.harness.cf.client.api;

import com.google.gson.JsonObject;
import io.harness.cf.client.connector.Connector;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.dto.Target;
import java.util.function.Consumer;
import lombok.NonNull;

public class CfClient implements AutoCloseable {

  private static volatile CfClient instance;
  private InnerClient client;

  public CfClient() {
    client = null;
  }

  public CfClient(@NonNull final String sdkKey) {
    client = new InnerClient(sdkKey);
  }

  public CfClient(@NonNull final String sdkKey, final Config options) {
    client = new InnerClient(sdkKey, options);
  }

  public CfClient(@NonNull final Connector connector) {
    client = new InnerClient(connector);
  }

  public CfClient(@NonNull final Connector connector, final Config options) {
    client = new InnerClient(connector, options);
  }

  public static CfClient getInstance() {

    if (instance == null)
      synchronized (CfClient.class) {
        if (instance == null) {

          instance = new CfClient();
        }
      }
    return instance;
  }

  public void initialize(final String apiKey) {
    initialize(apiKey, Config.builder().build());
  }

  public void initialize(final String apiKey, final Config config) {
    client = new InnerClient(apiKey, config);
  }

  public void initialize(@NonNull final Connector connector) {
    client = new InnerClient(connector);
  }

  public void initialize(@NonNull final Connector connector, final Config options) {
    client = new InnerClient(connector, options);
  }

  public void waitForInitialization() throws InterruptedException, FeatureFlagInitializeException {
    client.waitForInitialization();
  }

  public void on(@NonNull final Event event, @NonNull final Consumer<String> consumer) {
    client.on(event, consumer);
  }

  public void off() {
    client.off();
  }

  public void off(@NonNull final Event event) {
    client.off(event);
  }

  public void off(@NonNull final Event event, @NonNull final Consumer<String> consumer) {
    client.off(event, consumer);
  }

  public void update(@NonNull final Message message) {
    client.update(message, true);
  }

  public boolean boolVariation(
      @NonNull final String identifier, final Target target, final boolean defaultValue) {
    return client.boolVariation(identifier, target, defaultValue);
  }

  public String stringVariation(
      @NonNull String identifier, final Target target, @NonNull final String defaultValue) {
    return client.stringVariation(identifier, target, defaultValue);
  }

  public double numberVariation(
      @NonNull final String identifier, final Target target, final double defaultValue) {
    return client.numberVariation(identifier, target, defaultValue);
  }

  public JsonObject jsonVariation(
      @NonNull final String identifier,
      final Target target,
      @NonNull final JsonObject defaultValue) {
    return client.jsonVariation(identifier, target, defaultValue);
  }

  public void close() {
    client.close();
  }

  @Deprecated
  public void destroy() {
    close();
  }
}
