package io.harness.cf.client.dto;

import io.harness.cf.model.AuthenticationRequest;

public final class AuthenticationRequestBuilder {
  private String apiKey = null;

  private AuthenticationRequestBuilder() {}

  public static AuthenticationRequestBuilder anAuthenticationRequest() {
    return new AuthenticationRequestBuilder();
  }

  public AuthenticationRequestBuilder apiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  public AuthenticationRequest build() {
    AuthenticationRequest authenticationRequest = new AuthenticationRequest();
    authenticationRequest.setApiKey(apiKey);
    return authenticationRequest;
  }
}
