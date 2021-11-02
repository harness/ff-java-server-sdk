package io.harness.cf.client.api;

import com.google.gson.JsonObject;
import io.harness.cf.client.dto.Target;

interface Evaluation {

  boolean boolVariation(
      String identifier, Target target, boolean defaultValue, FlagEvaluateCallback callback);

  String stringVariation(
      String identifier, Target target, String defaultValue, FlagEvaluateCallback callback);

  double numberVariation(
      String identifier, Target target, double defaultValue, FlagEvaluateCallback callback);

  JsonObject jsonVariation(
      String identifier, Target target, JsonObject defaultValue, FlagEvaluateCallback callback);
}
