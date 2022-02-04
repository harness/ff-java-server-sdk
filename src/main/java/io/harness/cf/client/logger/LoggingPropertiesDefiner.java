package io.harness.cf.client.logger;

import ch.qos.logback.core.PropertyDefinerBase;
import io.harness.cf.Version;
import java.util.HashMap;
import java.util.Map;

public class LoggingPropertiesDefiner extends PropertyDefinerBase {
  private static final Map<String, String> properties = new HashMap<>();

  static {
    properties.put("version", Version.VERSION);
    properties.put("SDK", "Java");
  }

  private String propertyLookupKey;

  public void setPropertyLookupKey(String propertyLookupKey) {
    this.propertyLookupKey = propertyLookupKey;
  }

  @Override
  public String getPropertyValue() {
    return properties.get(propertyLookupKey);
  }
}
