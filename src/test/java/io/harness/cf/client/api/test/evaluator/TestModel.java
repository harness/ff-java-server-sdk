package io.harness.cf.client.api.test.evaluator;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;

import java.util.HashMap;
import java.util.List;

public class TestModel {

    public volatile String testFile;

    public FeatureConfig flag;
    public List<Target> targets;
    public List<Segment> segments;
    public HashMap<String, Boolean> expected;
}
