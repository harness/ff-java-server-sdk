package io.harness.cf.client.api;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
@Deprecated
public class Config extends BaseConfig {

  @Builder.Default private String configUrl = "https://config.ff.harness.io/api/1.0"; // Prod.

  @Builder.Default private String eventUrl = "https://events.ff.harness.io/api/1.0"; // Prod.

  /** timeout in milliseconds to connect to CF Server */
  @Builder.Default int connectionTimeout = 10000;
  /** timeout in milliseconds for reading data from CF Server */
  @Builder.Default int readTimeout = 30000;
  /** timeout in milliseconds for writing data to CF Server */
  @Builder.Default int writeTimeout = 10000;
}
