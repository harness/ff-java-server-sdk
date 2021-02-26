package io.harness.cf.client.api;

public class CfClientException extends Exception {
  public CfClientException(String errorMessage) {
    super(errorMessage);
  }
}
