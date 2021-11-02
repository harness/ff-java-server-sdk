package io.harness.cf.client.api;

import lombok.NonNull;

public interface RepositoryCallback {
  void onFlagStored(@NonNull String identifier);

  void onFlagDeleted(@NonNull String identifier);

  void onSegmentStored(@NonNull String identifier);

  void onSegmentDeleted(@NonNull String identifier);
}
