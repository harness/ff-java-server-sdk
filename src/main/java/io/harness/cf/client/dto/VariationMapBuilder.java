package io.harness.cf.client.dto;

import io.harness.cf.model.TargetMap;
import io.harness.cf.model.VariationMap;
import java.util.ArrayList;
import java.util.List;

public final class VariationMapBuilder {
  private String variation;
  private List<String> targets = null;
  private List<String> targetSegments = null;

  private VariationMapBuilder() {}

  public static VariationMapBuilder aVariationMap() {
    return new VariationMapBuilder();
  }

  public VariationMapBuilder variation(String variation) {
    this.variation = variation;
    return this;
  }

  public VariationMapBuilder targets(List<String> targets) {
    this.targets = targets;
    return this;
  }

  public VariationMapBuilder targetSegments(List<String> targetSegments) {
    this.targetSegments = targetSegments;
    return this;
  }

  public VariationMap build() {
    VariationMap variationMap = new VariationMap();
    variationMap.setVariation(variation);
    variationMap.setTargets(makeTargetMap(targets));
    variationMap.setTargetSegments(targetSegments);
    return variationMap;
  }

  private List<TargetMap> makeTargetMap(List<String> targets) {
    List<TargetMap> targetMapList = new ArrayList<>();
    for (String name : targets) {
      TargetMap targetMap = new TargetMap();
      targetMap.name(name);
      targetMapList.add(targetMap);
    }
    return targetMapList;
  }
}
