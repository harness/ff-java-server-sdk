package io.harness.cf.client.dto;

import io.harness.cf.model.*;
import java.util.ArrayList;
import java.util.List;

public final class FeatureConfigBuilder {
  private String project = null;
  private String environment = null;
  private String feature = null;
  private FeatureState state = null;
  private FeatureConfig.KindEnum kind = null;
  private List<Variation> variations = new ArrayList<>();
  private List<ServingRule> rules = null;
  private Serve defaultServe = null;
  private String offVariation = null;
  private List<Prerequisite> prerequisites = null;
  private Boolean archived = null;
  private String defaultOnVariation = null;
  private String defaultOffVariation = null;
  private List<VariationMap> variationToTargetMap = null;

  private FeatureConfigBuilder() {}

  public static FeatureConfigBuilder aFeatureConfig() {
    return new FeatureConfigBuilder();
  }

  public FeatureConfigBuilder project(String project) {
    this.project = project;
    return this;
  }

  public FeatureConfigBuilder environment(String environment) {
    this.environment = environment;
    return this;
  }

  public FeatureConfigBuilder feature(String feature) {
    this.feature = feature;
    return this;
  }

  public FeatureConfigBuilder state(FeatureState state) {
    this.state = state;
    return this;
  }

  public FeatureConfigBuilder kind(FeatureConfig.KindEnum kind) {
    this.kind = kind;
    return this;
  }

  public FeatureConfigBuilder variations(List<Variation> variations) {
    this.variations = variations;
    return this;
  }

  public FeatureConfigBuilder rules(List<ServingRule> rules) {
    this.rules = rules;
    return this;
  }

  public FeatureConfigBuilder defaultServe(Serve defaultServe) {
    this.defaultServe = defaultServe;
    return this;
  }

  public FeatureConfigBuilder offVariation(String offVariation) {
    this.offVariation = offVariation;
    return this;
  }

  public FeatureConfigBuilder prerequisites(List<Prerequisite> prerequisites) {
    this.prerequisites = prerequisites;
    return this;
  }

  public FeatureConfigBuilder archived(Boolean archived) {
    this.archived = archived;
    return this;
  }

  public FeatureConfigBuilder defaultOnVariation(String defaultOnVariation) {
    this.defaultOnVariation = defaultOnVariation;
    return this;
  }

  public FeatureConfigBuilder defaultOffVariation(String defaultOffVariation) {
    this.defaultOffVariation = defaultOffVariation;
    return this;
  }

  public FeatureConfigBuilder variationToTargetMap(List<VariationMap> variationToTargetMap) {
    this.variationToTargetMap = variationToTargetMap;
    return this;
  }

  public FeatureConfig build() {
    FeatureConfig featureConfig = new FeatureConfig();
    featureConfig.setProject(project);
    featureConfig.setEnvironment(environment);
    featureConfig.setFeature(feature);
    featureConfig.setState(state);
    featureConfig.setKind(kind);
    featureConfig.setVariations(variations);
    featureConfig.setRules(rules);
    featureConfig.setDefaultServe(defaultServe);
    featureConfig.setOffVariation(offVariation);
    featureConfig.setPrerequisites(prerequisites);
    featureConfig.setVariationToTargetMap(variationToTargetMap);
    return featureConfig;
  }
}
