package io.harness.cf.client.dto;

import io.harness.cf.model.Clause;
import io.harness.cf.model.Serve;
import io.harness.cf.model.ServingRule;
import java.util.ArrayList;
import java.util.List;

public final class ServingRuleBuilder {
  private Integer order = null;
  private List<Clause> clauses = new ArrayList<>();
  private Serve serve = null;

  private ServingRuleBuilder() {}

  public static ServingRuleBuilder aServingRule() {
    return new ServingRuleBuilder();
  }

  public ServingRuleBuilder order(Integer order) {
    this.order = order;
    return this;
  }

  public ServingRuleBuilder clauses(List<Clause> clauses) {
    this.clauses = clauses;
    return this;
  }

  public ServingRuleBuilder serve(Serve serve) {
    this.serve = serve;
    return this;
  }

  public ServingRule build() {
    ServingRule servingRule = new ServingRule();
    servingRule.setPriority(order);
    servingRule.setClauses(clauses);
    servingRule.setServe((Serve) serve);
    return servingRule;
  }
}
