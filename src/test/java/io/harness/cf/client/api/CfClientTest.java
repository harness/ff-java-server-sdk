package io.harness.cf.client.api;

import static com.google.common.util.concurrent.Service.State.RUNNING;
import static io.harness.cf.client.api.TestUtils.*;
import static io.harness.cf.client.api.dispatchers.CannedResponses.makeDummyJwtToken;
import static io.harness.cf.client.api.dispatchers.CannedResponses.makeMockJsonResponse;
import static io.harness.cf.client.connector.HarnessConnectorUtils.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import io.harness.cf.client.api.dispatchers.*;
import io.harness.cf.client.api.testutils.DummyConnector;
import io.harness.cf.client.common.Cache;
import io.harness.cf.client.dto.Target;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.NonNull;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CfClientTest {

  private final MockWebServer mockWebServer = new MockWebServer();
  private static final AtomicInteger onEventCounter = new AtomicInteger();

  private static final Target target =
      Target.builder().identifier("andybody@harness.io").name("andybody@harness.io").build();

  @AfterEach
  void afterAll() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void shouldGetInstanceWithoutErrors() {
    assertDoesNotThrow(CfClient::getInstance, "Exception should not be thrown");
  }

  @Test
  void defaultConstructorShouldNotThrowException() {
    assertDoesNotThrow(
        () -> {
          try (CfClient client = new CfClient(); ) {
            client.initialize("dummyKey");
          }
        },
        "Exception should not be thrown");
  }

  private interface AssertOptions {
    void assertOptions(BaseConfig config);
  }

  @Test
  void testConstructors() throws IOException {

    final Config config =
        Config.builder()
            .streamEnabled(false)
            .pollIntervalInSeconds(123)
            .analyticsEnabled(false)
            .globalTargetEnabled(false)
            .frequency(321)
            .bufferSize(999)
            .allAttributesPrivate(true)
            .privateAttributes(ImmutableSet.of("privateFeature1", "privateFeature2"))
            .metricsServiceAcceptableDuration(9999)
            .debug(true)
            .cache(new DummyCache())
            .build();

    final AssertOptions asserter =
        actualOptions -> {
          assertFalse(actualOptions.isStreamEnabled());
          assertEquals(123, actualOptions.getPollIntervalInSeconds());
          assertFalse(actualOptions.isAnalyticsEnabled());
          assertFalse(actualOptions.isGlobalTargetEnabled());
          assertEquals(321, actualOptions.getFrequency());
          assertEquals(999, actualOptions.getBufferSize());
          assertTrue(actualOptions.isAllAttributesPrivate());
          assertInstanceOf(Set.class, config.getPrivateAttributes());
          assertEquals(2, config.getPrivateAttributes().size());
          assertTrue(config.getPrivateAttributes().contains("privateFeature1"));
          assertTrue(config.getPrivateAttributes().contains("privateFeature2"));
          assertEquals(9999, config.getMetricsServiceAcceptableDuration());
          assertTrue(actualOptions.isDebug());
          assertInstanceOf(DummyCache.class, config.getCache());
        };

    try (final CfClient client = new CfClient("testsdkkey", config)) {
      final BaseConfig actualOptions = client.getInnerClient().getOptions();
      asserter.assertOptions(actualOptions);
    }

    try (final CfClient client = new CfClient("testsdkkey", (BaseConfig) config)) {
      final BaseConfig actualOptions = client.getInnerClient().getOptions();
      asserter.assertOptions(actualOptions);
    }
  }

  @Test
  void shouldFailIfEmptySdkKeyGiven() {
    Exception thrown =
        assertThrows(
            IllegalArgumentException.class, () -> new CfClient(""), "Exception was not thrown");
    assertInstanceOf(IllegalArgumentException.class, thrown);
  }

  @Test
  void shouldAuthenticatedAgainstAuthEndpoint() throws IOException, URISyntaxException {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(false)
            .streamEnabled(false)
            .build();

    MockResponse mockedSuccessResponse =
        makeMockJsonResponse(200, "{\"authToken\": \"" + makeDummyJwtToken() + "\"}");
    MockResponse mockedFlagsSuccessResponse = makeMockJsonResponse(200, makeFeatureJson());
    MockResponse mockedSegmentSuccessResponse = makeMockJsonResponse(200, makeSegmentsJson());

    mockWebServer.enqueue(mockedSuccessResponse);
    mockWebServer.enqueue(
        mockedFlagsSuccessResponse); // Return some flags+segments to allow init to finish
    mockWebServer.enqueue(mockedSegmentSuccessResponse);
    mockWebServer.start();

    try (CfClient client =
        new CfClient(makeConnector(mockWebServer.getHostName(), mockWebServer.getPort()), config)) {
      assertTimeoutPreemptively(
          Duration.ofMillis(30_000),
          client::waitForInitialization,
          "CfClient did not initialize on time");
    }
  }

  private static Stream<Arguments> variantsToTest() {
    return Stream.of(
        Arguments.of("boolVariation", (Consumer<CfClient>) CfClientTest::testBoolVariant),
        Arguments.of("stringVariation", (Consumer<CfClient>) CfClientTest::testStringVariant),
        Arguments.of("numberVariation", (Consumer<CfClient>) CfClientTest::testNumberVariant),
        Arguments.of("jsonVariant", (Consumer<CfClient>) CfClientTest::testJsonVariant),
        Arguments.of(
            "boolVariationWithNullTarget",
            (Consumer<CfClient>) CfClientTest::testBoolVariantWithNullTarget),
        Arguments.of(
            "stringVariationWithNullTarget",
            (Consumer<CfClient>) CfClientTest::testStringVariantWithNullTarget),
        Arguments.of(
            "numberVariationWithNullTarget",
            (Consumer<CfClient>) CfClientTest::testNumberVariantWithNullTarget),
        Arguments.of(
            "jsonVariantWithNullTarget",
            (Consumer<CfClient>) CfClientTest::testJsonVariantWithNullTarget));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("variantsToTest")
  void shouldGetVariantInPollingMode(String description, Consumer<CfClient> flagCallback)
      throws IOException, URISyntaxException {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(true)
            .streamEnabled(false)
            .build();

    MockResponse mockedAuthResponse =
        makeMockJsonResponse(200, "{\"authToken\": \"" + makeDummyJwtToken() + "\"}");
    MockResponse mockedFlagsResponse = makeMockJsonResponse(200, makeBasicFeatureJson());
    MockResponse mockedSegmentResponse = makeMockJsonResponse(200, makeSegmentsJson());
    mockWebServer.enqueue(mockedAuthResponse);
    mockWebServer.enqueue(mockedFlagsResponse);
    mockWebServer.enqueue(mockedSegmentResponse);
    mockWebServer.start();

    try (CfClient client =
        new CfClient(makeConnector(mockWebServer.getHostName(), mockWebServer.getPort()), config)) {
      assertTimeoutPreemptively(
          Duration.ofMillis(30_000),
          client::waitForInitialization,
          "CfClient did not initialize on time");

      flagCallback.accept(client); // Run the actual test
    }
  }

  private static void testBoolVariant(CfClient client) {
    boolean value = client.boolVariation("simplebool", target, false);
    assertTrue(value);
  }

  private static void testStringVariant(CfClient client) {
    String value = client.stringVariation("simplestring", target, "DEFAULT");
    assertEquals("on-string", value);
  }

  private static void testNumberVariant(CfClient client) {
    double value = client.numberVariation("simplenumber", target, 0.123);
    assertEquals(1, value);
  }

  private static void testJsonVariant(CfClient client) {
    JsonObject value = client.jsonVariation("simplejson", target, new JsonObject());
    assertEquals("on", value.get("value").getAsString());
  }

  private static void testBoolVariantWithNullTarget(CfClient client) {
    boolean value = client.boolVariation("simplebool", null, false);
    assertTrue(value);
  }

  private static void testStringVariantWithNullTarget(CfClient client) {
    String value = client.stringVariation("simplestring", null, "DEFAULT");
    assertEquals("on-string", value);
  }

  private static void testNumberVariantWithNullTarget(CfClient client) {
    double value = client.numberVariation("simplenumber", null, 0.123);
    assertEquals(1, value);
  }

  private static void testJsonVariantWithNullTarget(CfClient client) {
    JsonObject value = client.jsonVariation("simplejson", null, new JsonObject());
    assertEquals("on", value.get("value").getAsString());
  }

  @Test
  void streamPatchTest() throws Exception {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(false)
            .streamEnabled(true)
            .build();

    try (MockWebServer mockSvr = new MockWebServer()) {
      mockSvr.setDispatcher(new TestWebServerDispatcher());
      mockSvr.start();

      try (CfClient client =
          new CfClient(makeConnector(mockSvr.getHostName(), mockSvr.getPort()), config)) {
        final CountDownLatch latch = new CountDownLatch(2);

        client.on(
            Event.CHANGED,
            e -> {
              System.out.println("GOT EVENT -----> " + e);

              if ("simplebool".equals(e)) {
                latch.countDown();
              }
            });

        final boolean success = latch.await(30, TimeUnit.SECONDS);
        assertTrue(success, "Missed 1 or more patch events for flag simplebool!");
      }
    }
  }

  @Test
  void shouldNotReconnectToStreamEndpointIfEndpointReturns501Unimplemented() throws Exception {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(false)
            .streamEnabled(true)
            .build();

    // This will return 501 when /stream endpoint is connected to
    UnimplementedStreamDispatcher webserverDispatcher = new UnimplementedStreamDispatcher(1);

    try (MockWebServer mockSvr = new MockWebServer()) {
      mockSvr.setDispatcher(webserverDispatcher);
      mockSvr.start();

      try (CfClient client =
          new CfClient(
              makeConnectorWithMinimalRetryBackOff(mockSvr.getHostName(), mockSvr.getPort()),
              config)) {

        client.waitForInitialization();

        // we should only get one connection to /stream within this window of time
        webserverDispatcher.waitForAllConnections(15);
        assertEquals(
            1,
            webserverDispatcher.getStreamEndpointCount().get(),
            "there should only be 1 connection to /stream endpoint");
        assertEquals(
            RUNNING,
            client.getInnerClient().getPollProcessor().state(),
            "poller not in RUNNING state");
      }
    }
  }

  private static void onEvent(String e) {
    if ("simplebool".equals(e)) {
      onEventCounter.incrementAndGet();
    }
  }

  @Test
  void testOnOffUpdate() throws Exception {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(false)
            .streamEnabled(false)
            .build();

    try (final CfClient client = new CfClient(new DummyConnector(), config)) {
      onEventCounter.set(0);
      client.on(Event.CHANGED, CfClientTest::onEvent);
      client.getInnerClient().notifyConsumers(Event.CHANGED, "simplebool");
      client.getInnerClient().notifyConsumers(Event.CHANGED, "simplebool");
      client.off();
      client.getInnerClient().notifyConsumers(Event.CHANGED, "simplebool");
      assertEquals(2, onEventCounter.get());

      onEventCounter.set(0);
      client.on(Event.CHANGED, CfClientTest::onEvent);
      client.getInnerClient().notifyConsumers(Event.CHANGED, "simplebool");
      client.getInnerClient().notifyConsumers(Event.CHANGED, "simplebool");
      client.off(Event.CHANGED);
      client.getInnerClient().notifyConsumers(Event.CHANGED, "simplebool");
      assertEquals(2, onEventCounter.get());
    }
  }

  @Test
  void shouldRetryThenReAuthenticateWithoutThrowingIllegalStateException() throws Exception {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(false)
            .streamEnabled(false)
            .build();

    Http4xxOnFirstAuthDispatcher webserverDispatcher = new Http4xxOnFirstAuthDispatcher(4);

    try (MockWebServer mockSvr = new MockWebServer()) {
      mockSvr.setDispatcher(webserverDispatcher);
      mockSvr.start();

      try (CfClient client =
          new CfClient(
              makeConnectorWithMinimalRetryBackOff(mockSvr.getHostName(), mockSvr.getPort()),
              config)) {

        client.waitForInitialization();

        // First 3 attempts to connect to auth endpoint will return a 4xx, followed by a 200 success
        webserverDispatcher.waitForAllConnections(15);

        final int expectedAuths = 3 + 3 + 1; // 3+3 failed retries (4xx), 1 success (200)

        assertEquals(
            expectedAuths,
            webserverDispatcher.getAuthAttempts().get(),
            "not enough authentication attempts");
      }
    }
  }

  @Test
  void shouldRetryThenReAuthenticateWhen403IsReturnedOnGetAllSegments() throws Exception {
    BaseConfig config =
        BaseConfig.builder()
            .pollIntervalInSeconds(1)
            .analyticsEnabled(false)
            .streamEnabled(false)
            .debug(false)
            .build();

    Http4xxOnGetAllSegmentsDispatcher webserverDispatcher =
        new Http4xxOnGetAllSegmentsDispatcher(2, 5);

    try (MockWebServer mockSvr = new MockWebServer()) {
      mockSvr.setDispatcher(webserverDispatcher);
      mockSvr.start();

      try (CfClient client =
          new CfClient(
              makeConnectorWithMinimalRetryBackOff(mockSvr.getHostName(), mockSvr.getPort()),
              config)) {

        client.waitForInitialization();

        // Auth will return success on first go, then randomly fail getAllSegments with a 4xx, we
        // want it to reauthenticate
        webserverDispatcher.waitForAllConnections(15);

        assertEquals(
            2, webserverDispatcher.getAuthAttempts().get(), "not enough authentication attempts");
      }
    }
  }

  static class DummyCache implements Cache {

    @Override
    public void set(@NonNull String key, @NonNull Object value) {}

    @Override
    public Object get(@NonNull String key) {
      return null;
    }

    @Override
    public void delete(@NonNull String key) {}

    @Override
    public List<String> keys() {
      return null;
    }
  }
}
