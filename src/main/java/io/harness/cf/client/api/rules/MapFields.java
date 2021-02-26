package io.harness.cf.client.api.rules;

public class MapFields {

  private final String key;

  private final String value;

  public MapFields(String key) {
    this.key = key;
    this.value = "";
  }

  public MapFields(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }
}
