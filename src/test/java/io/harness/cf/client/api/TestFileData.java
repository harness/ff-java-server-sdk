package io.harness.cf.client.api;

import io.harness.cf.client.dto.Target;
import io.harness.cf.model.FeatureConfig;
import io.harness.cf.model.Segment;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Builder
@Data
@AllArgsConstructor
@ToString
public class TestFileData {
  private String testFile;
  private List<FeatureConfig> flags;
  private List<Target> targets;
  private List<Segment> segments;
  private List<Map<String, Object>> tests;
}
