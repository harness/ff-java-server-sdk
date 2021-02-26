package io.harness.cf.client.api.rules;

import io.harness.cf.client.dto.Target;

public class AttributeType {

  private final String name;
  private final Target target;

  public AttributeType(String name, Target target) {
    this.name = name;
    this.target = target;
  }

  private String loadAttributeName() {
    // One level for the moment, in the future json path should be used.
    return parseMapField().getKey();
  }

  private String loadValue() {
    return null;
  }

  private MapFields parseMapField() {
    //noinspection SuspiciousRegexArgument
    String[] fields = name.split(".");
    return fields.length > 1 ? new MapFields(fields[0], fields[1]) : new MapFields(fields[0]);
  }

  public String getName() {
    return name;
  }

  public Object getTarget() {
    return target;
  }
}
