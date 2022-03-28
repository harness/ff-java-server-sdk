package io.harness.cf.client.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TestCase {
  private final String file;
  private final String targetIdentifier;
  private final Object expectedValue;
  private final TestFileData fileData;
  private final String testName;
}
