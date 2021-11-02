package io.harness.cf.client.api;

import com.google.gson.JsonObject;
import io.harness.cf.client.dto.Target;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum CfClient {
  INSTANCE;

  private InnerClient client;

  public void init(@NonNull final String sdkKey) {
    if (client == null) {
      client = new InnerClient(sdkKey);
    }
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
}
