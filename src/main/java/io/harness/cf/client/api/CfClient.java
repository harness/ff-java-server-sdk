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

  public CfClient(@NonNull String sdkKey) {
    client = new InnerClient(sdkKey);
  }

  public CfClient(@NonNull String sdkKey, Config options) {
    client = new InnerClient(sdkKey, options);
  }

  public CfClient(@NonNull final Connector connector) {
    client = new InnerClient(connector);
  }

  public CfClient(@NonNull Connector connector, final Config options) {
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

  public void initialize(@NonNull Connector connector, final Config options) {
    client = new InnerClient(connector, options);
  }

  public void waitForInitialization() throws InterruptedException, FeatureFlagInitializeException {
    client.waitForInitialization();
  }

  public void on(@NonNull Event event, @NonNull Consumer<String> consumer) {
    client.on(event, consumer);
  }

  public void off() {
    client.off();
  }

  public void off(@NonNull Event event) {
    client.off(event);
  }

  public void off(@NonNull Event event, @NonNull Consumer<String> consumer) {
    client.off(event, consumer);
  }

  public void update(@NonNull Message message) {
    client.update(message, true);
  }

  public boolean boolVariation(@NonNull String identifier, Target target, boolean defaultValue) {
    return client.boolVariation(identifier, target, defaultValue);
  }

  public String stringVariation(
      @NonNull String identifier, Target target, @NonNull String defaultValue) {
    return client.stringVariation(identifier, target, defaultValue);
  }

  public double numberVariation(@NonNull String identifier, Target target, double defaultValue) {
    return client.numberVariation(identifier, target, defaultValue);
  }

  public JsonObject jsonVariation(
      @NonNull String identifier, Target target, @NonNull JsonObject defaultValue) {
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
