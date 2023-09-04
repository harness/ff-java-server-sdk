package io.harness.cf.client.connector;

import static org.junit.jupiter.api.Assertions.*;

import io.harness.cf.model.Metrics;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LocalConnectorTest {

  @Test
  void shouldNotThrowExceptionsIfFoldersDontExist() {
    String source = "LocalConnectorTestSource";

    Metrics metrics = new Metrics();
    metrics.metricsData(new ArrayList<>());
    metrics.targetData(new ArrayList<>());

    assertDoesNotThrow(
        () -> {
          LocalConnector connector = new LocalConnector(source);
          connector.listFiles(source, "domain");
          connector.getFlags();
          connector.getSegments();
          connector.postMetrics(metrics);
        });
  }
}
