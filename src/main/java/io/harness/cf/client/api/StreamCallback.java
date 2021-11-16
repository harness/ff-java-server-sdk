package io.harness.cf.client.api;

interface StreamCallback {

  void onStreamConnected();

  void onStreamDisconnected();

  void onStreamReady();

  void onStreamError();
}
