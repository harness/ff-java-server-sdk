package io.harness.cf.client.api.evaluator;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;

import java.util.HashMap;
import java.util.List;

class TestModel {

    FeatureConfig flag;
    List<Target> targets;
    List<Segment> segments;
    HashMap<String, Boolean> expected;
}
