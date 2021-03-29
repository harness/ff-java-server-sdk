package io.harness.cf.client.dto;

import io.harness.cf.model.Target;
import java.util.ArrayList;
import java.util.List;

public final class SegmentBuilder {
  private String identifier = null;
  private String name = null;
  private String environment = null;
  private List<io.harness.cf.model.Tag> tags = null;
  private List<String> included = null;
  private List<String> excluded = null;
  private List<io.harness.cf.model.Clause> rules = null;
  private Long createdAt = null;
  private Long modifiedAt = null;
  private Long version = null;

  private SegmentBuilder() {}

  public static SegmentBuilder aTargetSegment() {
    return new SegmentBuilder();
  }

  public SegmentBuilder withIdentifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public SegmentBuilder withName(String name) {
    this.name = name;
    return this;
  }

  public SegmentBuilder withEnvironment(String environment) {
    this.environment = environment;
    return this;
  }

  public SegmentBuilder withTags(List<io.harness.cf.model.Tag> tags) {
    this.tags = tags;
    return this;
  }

  public SegmentBuilder withIncluded(List<String> included) {
    this.included = included;
    return this;
  }

  public SegmentBuilder withExcluded(List<String> excluded) {
    this.excluded = excluded;
    return this;
  }

  public SegmentBuilder withRules(List<io.harness.cf.model.Clause> rules) {
    this.rules = rules;
    return this;
  }

  public SegmentBuilder withCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public SegmentBuilder withModifiedAt(Long modifiedAt) {
    this.modifiedAt = modifiedAt;
    return this;
  }

  public SegmentBuilder withVersion(Long version) {
    this.version = version;
    return this;
  }

  public io.harness.cf.model.Segment build() {
    io.harness.cf.model.Segment targetSegment = new io.harness.cf.model.Segment();
    targetSegment.setIdentifier(identifier);
    targetSegment.setName(name);
    targetSegment.setEnvironment(environment);
    targetSegment.setTags(tags);
    targetSegment.setIncluded(makeSegmentList(included));
    targetSegment.setExcluded(makeSegmentList(excluded));
    targetSegment.setRules(rules);
    targetSegment.setCreatedAt(createdAt);
    targetSegment.setModifiedAt(modifiedAt);
    targetSegment.setVersion(version);
    return targetSegment;
  }

  private List<Target> makeSegmentList(List<String> targets) {
    List<Target> targetList = new ArrayList<>();
    for (String name : targets) {
      Target target = new Target();
      target.name(name);
      targetList.add(target);
    }
    return targetList;
  }
}
