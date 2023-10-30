package io.harness.cf.client.dto;

import io.harness.cf.client.common.StringUtils;
import java.util.Map;
import java.util.Set;
import lombok.*;
import lombok.experimental.Accessors;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Target {

  private String name;
  private String identifier;

  @Singular private Map<String, Object> attributes;

  @Deprecated // private attributes will be removed in a future release
  @Getter
  @Accessors(fluent = true)
  private Boolean isPrivate = false;

  @Deprecated @Singular private Set<String> privateAttributes;

  @Override
  public String toString() {

    return "TargetId: " + identifier;
  }

  public boolean isValid() {

    return !StringUtils.isNullOrEmpty(identifier);
  }

  public io.harness.cf.model.Target ApiTarget() {
    return io.harness.cf.model.Target.builder()
        .identifier(getIdentifier())
        .name(getName())
        .attributes(getAttributes())
        .build();
  }
}
