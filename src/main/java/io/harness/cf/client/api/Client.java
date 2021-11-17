package io.harness.cf.client.api;

import com.google.gson.JsonObject;
import io.harness.cf.client.dto.Message;
import io.harness.cf.client.dto.Target;
import java.util.function.Consumer;
import lombok.NonNull;

public class Client implements AutoCloseable {
  private final InnerClient innerClient;

  public Client(@NonNull String sdkKey) {
    innerClient = new InnerClient(sdkKey);
  }

  public Client(@NonNull String sdkKey, Config options) {
    innerClient = new InnerClient(sdkKey, options);
  }

  public void waitForInitialization() throws InterruptedException {
    innerClient.waitForInitialization();
  }

  public void on(@NonNull Event event, @NonNull Consumer<String> consumer) {
    innerClient.on(event, consumer);
  }

  public void off() {
    innerClient.off();
  }

  public void off(@NonNull Event event) {
    innerClient.off(event);
  }

  public void off(@NonNull Event event, @NonNull Consumer<String> consumer) {
    innerClient.off(event, consumer);
  }

  public void update(@NonNull Message message) {
    innerClient.update(message);
  }

  public boolean boolVariation(@NonNull String identifier, Target target, boolean defaultValue) {
    return innerClient.boolVariation(identifier, target, defaultValue);
  }

  public String stringVariation(
      @NonNull String identifier, Target target, @NonNull String defaultValue) {
    return innerClient.stringVariation(identifier, target, defaultValue);
  }

  public double numberVariation(@NonNull String identifier, Target target, double defaultValue) {
    return innerClient.numberVariation(identifier, target, defaultValue);
  }

  public JsonObject jsonVariation(
      @NonNull String identifier, Target target, @NonNull JsonObject defaultValue) {
    return innerClient.jsonVariation(identifier, target, defaultValue);
  }

  public void close() {
    innerClient.close();
  }
}
