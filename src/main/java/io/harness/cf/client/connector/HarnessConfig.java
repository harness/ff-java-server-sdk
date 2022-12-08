package io.harness.cf.client.connector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@ToString
public class HarnessConfig {
  public static final int MIN_FREQUENCY = 60;

  @Builder.Default private String configUrl = "https://config.ff.harness.io/api/1.0"; // Prod.

  @Builder.Default private String eventUrl = "https://events.ff.harness.io/api/1.0"; // Prod.

  /** timeout in milliseconds to connect to CF Server */
  @Builder.Default int connectionTimeout = 10000;
  /** timeout in milliseconds for reading data from CF Server */
  @Builder.Default int readTimeout = 30000;
  /** timeout in milliseconds for writing data to CF Server */
  @Builder.Default int writeTimeout = 10000;

  /** read timeout in minutes for SSE connections */
  @Builder.Default long sseReadTimeout = 1;
}
