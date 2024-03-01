package io.harness.cf.client.common;

import static java.lang.String.valueOf;
import static java.util.Optional.*;

import io.harness.cf.client.dto.Target;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SdkCodes {

  public static void errorMissingSdkKey() {
    log.error(sdkErrMsg(1002));
  }

  public static void infoPollStarted(int durationSec) {
    log.info(sdkErrMsg(4000, of(valueOf(durationSec * 1000))));
  }

  public static void infoSdkInitOk() {
    log.info(sdkErrMsg(1000));
  }

  public static void infoSdkAuthOk() {
    log.info(sdkErrMsg(2000));
  }

  public static void infoPollingStopped() {
    log.info(sdkErrMsg(4001));
  }

  public static void infoStreamConnected() {
    log.info(sdkErrMsg(5000));
  }

  public static void infoStreamEventReceived(String eventJson) {
    log.info(sdkErrMsg(5002, ofNullable(eventJson)));
  }

  public static void infoMetricsThreadStarted(int intervalSec) {
    log.info(sdkErrMsg(7000, of(valueOf(intervalSec * 1000))));
  }

  public static void infoMetricsThreadExited() {
    log.info(sdkErrMsg(7001));
  }

  public static void warnAuthFailedSrvDefaults(String reason) {
    log.warn(sdkErrMsg(2001, Optional.ofNullable(reason)));
  }

  public static void warnAuthRetying(int attempt) {
    log.warn(sdkErrMsg(2003, Optional.of(", attempt " + attempt)));
  }

  public static void warnStreamDisconnected(String reason) {
    log.warn(sdkErrMsg(5001, Optional.ofNullable(reason)));
  }

  public static void warnPostMetricsFailed(String reason) {
    log.warn(sdkErrMsg(7002, Optional.ofNullable(reason)));
  }


  public static void warnMetricsBufferFull() {
    log.warn(sdkErrMsg(7008, Optional.empty()));
  }


  public static void warnDefaultVariationServed(String identifier, Target target, String def) {
    String targetId = (target == null) ? "null" : target.getIdentifier();
    String msg = String.format("identifier=%s, target=%s, default=%s", identifier, targetId, def);
    log.warn(sdkErrMsg(6001, of(msg)));
  }

  public static void warnBucketByAttributeNotFound(String bucketBy, String usingValue) {
    String msg = String.format("missing=%s, using value=%s", bucketBy, usingValue);
    log.warn(sdkErrMsg(6002, of(msg)));
  }

  private static final Map<Integer, String> MAP =
      Arrays.stream(
              new String[][] {
                // SDK_INIT_1xxx
                {"1000", "The SDK has successfully initialized"},
                {
                  "1001",
                  "The SDK has failed to initialize due to the following authentication error:"
                },
                {"1002", "The SDK has failed to initialize due to a missing or empty API key"},
                // SDK_AUTH_2xxx
                {"2000", "Authenticated ok"},
                {
                  "2001",
                  "Authentication failed with a non-recoverable error - defaults will be served"
                },
                {"2003", "Retrying to authenticate"},
                // SDK_POLL_4xxx
                {"4000", "Polling started, intervalMs:"},
                {"4001", "Polling stopped"},
                // SDK_STREAM_5xxx
                {"5000", "SSE stream connected ok"},
                {"5001", "SSE stream disconnected, reason:"},
                {"5002", "SSE event received:"},
                {"5003", "SSE retrying to connect in"},
                // SDK_EVAL_6xxx
                {"6000", "Evaluated variation successfully"},
                {"6001", "Default variation was served"},
                {
                  "6002",
                  "BucketBy attribute not found in target attributes, falling back to 'identifier':"
                },
                // SDK_METRICS_7xxx
                {"7000", "Metrics thread started, intervalMs:"},
                {"7001", "Metrics thread exited"},
                {"7002", "Posting metrics failed, reason:"},
                {"7008", "Metrics buffer is full and metrics will be discarded"}

              })
          .collect(Collectors.toMap(entry -> Integer.parseInt(entry[0]), entry -> entry[1]));

  private static String sdkErrMsg(int error_code) {
    return sdkErrMsg(error_code, Optional.empty());
  }

  private static String sdkErrMsg(int error_code, Optional<String> appendText) {
    return String.format(
        "SDKCODE(%s:%s): %s %s",
        getErrClass(error_code), error_code, MAP.get(error_code), appendText.orElse(""));
  }

  private static String getErrClass(int error_code) {
    if (error_code >= 1000 && error_code <= 1999) return "init";
    else if (error_code >= 2000 && error_code <= 2999) return "auth";
    else if (error_code >= 4000 && error_code <= 4999) return "poll";
    else if (error_code >= 5000 && error_code <= 5999) return "stream";
    else if (error_code >= 6000 && error_code <= 6999) return "eval";
    else if (error_code >= 7000 && error_code <= 7999) return "metric";
    return "";
  }
}
