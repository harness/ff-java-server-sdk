package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.HashMap;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class TestFileData {
  private String testFile;
  private FeatureConfig flag;
  private List<Target> targets;
  private List<Segment> segments;
  private HashMap<String, Boolean> expected;
}
