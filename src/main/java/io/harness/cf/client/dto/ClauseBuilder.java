package io.harness.cf.client.dto;

import io.harness.cf.model.Clause;
import java.util.List;

public final class ClauseBuilder {
  private String attribute;
  private String op;
  private List<String> value;
  private Boolean negate;

  private ClauseBuilder() {}

  public static ClauseBuilder aClause() {
    return new ClauseBuilder();
  }

  public ClauseBuilder withAttribute(String attribute) {
    this.attribute = attribute;
    return this;
  }

  public ClauseBuilder withOp(String op) {
    this.op = op;
    return this;
  }

  public ClauseBuilder withValue(List<String> value) {
    this.value = value;
    return this;
  }

  public ClauseBuilder withNegate(Boolean negate) {
    this.negate = negate;
    return this;
  }

  public Clause build() {
    Clause clause = new Clause();
    clause.setAttribute(attribute);
    clause.setOp(op);
    clause.setValues(value);
    clause.setNegate(negate);
    return clause;
  }
}
