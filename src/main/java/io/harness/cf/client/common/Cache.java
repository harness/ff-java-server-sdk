package io.harness.cf.client.common;

import java.util.List;
import lombok.NonNull;

public interface Cache {
  void set(@NonNull String key, @NonNull Object value);

  Object get(@NonNull String key);

  void delete(@NonNull String key);

  List<String> keys();
}
