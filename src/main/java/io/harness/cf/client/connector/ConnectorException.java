package io.harness.cf.client.connector;

public class ConnectorException extends Exception {

  private final int httpCode;
  private final String httpReason;

  public ConnectorException(String message) {
    super(message);
    this.httpCode = 0;
    this.httpReason = "";
  }

  public ConnectorException(String message, int httpCode, String httpMsg) {
    super(message);
    this.httpCode = httpCode;
    this.httpReason = httpMsg;
  }

  @Override
  public String getMessage() {
    final String httpInfo =
        (httpCode == 0) ? "" : String.format(" - httpCode=%d httpReason=%s", httpCode, httpReason);
    return super.getMessage() + httpInfo;
  }
}
