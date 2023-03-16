package io.harness.cf.client.api.dispatchers;

public class Endpoints {

  public static final String AUTH_ENDPOINT = "/api/1.0/client/auth";
  public static final String FEATURES_ENDPOINT =
      "/api/1.0/client/env/00000000-0000-0000-0000-000000000000/feature-configs?cluster=1";
  public static final String SEGMENTS_ENDPOINT =
      "/api/1.0/client/env/00000000-0000-0000-0000-000000000000/target-segments?cluster=1";
  public static final String STREAM_ENDPOINT = "/api/1.0/stream?cluster=1";
  public static final String SIMPLE_BOOL_FLAG_ENDPOINT =
      "/api/1.0/client/env/00000000-0000-0000-0000-000000000000/feature-configs/simplebool?cluster=1";
}
