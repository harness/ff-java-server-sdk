Harness CF Java Server SDK
========================

## Overview

-------------------------
[Harness](https://www.harness.io/) is a feature management platform that helps teams to build better software and to
test features quicker.

-------------------------

## Setup

Add the following snippet to your project's `pom.xml` file:

```
<dependency>
    <groupId>io.harness</groupId>
    <artifactId>ff-java-server-sdk</artifactId>
    <version>[1.1.2,)</version>
</dependency>
```

## Cloning the SDK repository

In order to clone SDK repository properly perform cloning like in the following example:

```
git clone --recurse-submodules git@github.com:drone/ff-java-server-sdk.git
```

After dependency has been added, the SDK elements, primarily `CfClient` should be accessible in the main application.

## Initialization

`CfClient` is a base class that provides all features of SDK.

We can instantiate by calling the `getInstance()` method or by using public
constructors (making multiple instances).

```
/**
 * Put the API Key here from your environment
 */
String apiKey = "YOUR_API_KEY";

CfClient cfClient = CfClient.getInstance();
cfClient.initialize(apiKey);

/**
 * if you want wait for initialization use method waitForInitialization()
 * otherwise it will do in asynchronous manner
 */

/**
 * Define you target on which you would like to evaluate 
 * the featureFlag
 */
Target target = Target.builder()
                    .name("User1")
                    .attributes(new HashMap<String, Object>())
                    .identifier("user1@example.com")
                    .build();
```

`target` represents the desired target for which we want features to be evaluated.

`"YOUR_API_KEY"` is an authentication key, needed for access to Harness services.

**Your Harness SDK is now initialized. Congratulations!**

### Public API Methods ###

The Public API exposes a few methods that you can utilize:

initialize:
* `public void initialize(final String apiKey)`
* `public void initialize(final String apiKey, final Config config)`
* `public void initialize(@NonNull final Connector connector)`
* `public void initialize(@NonNull Connector connector, final Config options)`
* `public void waitForInitialization() throws InterruptedException, FeatureFlagInitializeException`

evaluations:
* `public boolean boolVariation(String key, Target target, boolean defaultValue)`
* `public String stringVariation(String key, Target target, String defaultValue)`
* `public double numberVariation(String key, Target target, int defaultValue)`
* `public JsonObject jsonVariation(String key, Target target, JsonObject defaultValue)`

react on event:
* `public void on(@NonNull Event event, @NonNull Consumer<String> consumer)`
* `public void off()`
* `public void off(@NonNull Event event)`
* `public void off(@NonNull Event event, @NonNull Consumer<String> consumer)`

manual update (webpush):
* `public void update(@NonNull Message message)`

close SDK:
* `public void destroy()` @deprecated
* `public void close()`

## Fetch evaluation's value

It is possible to fetch a value for a given evaluation. Evaluation is performed based on a different type. In case there
is no evaluation with provided id, the default value is returned.

Use the appropriate method to fetch the desired Evaluation of a certain type.

### Bool variation

```
boolean result = cfClient.boolVariation("sample_boolean_flag", target, false);  
```

### Number variation

```
double result = cfClient.numberVariation("sample_number_flag", target, 0);  
```

### String variation

```
boolean result = cfClient.stringVariation("sample_string_flag", target, "");  
```

## Using feature flags metrics

Metrics API endpoint can be changed like this:

```
Config.builder()
              .eventUrl("METRICS_API_EVENTS_URL")
              .build();
```

Otherwise, the default metrics endpoint URL will be used.

## Listen on events

```
client.on(Event.READY, result -> log.info("READY"));

client.on(Event.CHANGED, result -> log.info("Flag changed {}", result));
```

events will work even when streamEnabled is off.

## Connector

This is a new feature that allows you to create or use other connectors.
Connector is just a proxy to your data. Currently supported connectors:
* Harness
* Local (used only in development)

```
LocalConnector connector = new LocalConnector("./local");
client = new CfClient(connector);
```

## Storage

For offline support and asynchronous startup of SDK use storage interface.
When SDK is used without waitForInitialization method then it starts in async mode.
So all flags are loaded from last saved configurations and it will use that values.
If there is no flag on storage then it will be evaluated from defaultValue argument.

```
final FileMapStore fileStore = new FileMapStore("Non-Freemium");
LocalConnector connector = new LocalConnector("./local");
client = new CfClient(connector, Config.builder().store(fileStore).build());
```

## Update from controller or handler

this is useful only if webpush is used and in that case you need to disable
stream
```
Config.builder()
      .streamEnabled(false)
      .build();

cfClient.update(Message message)
```

## Shutting down the SDK

To avoid potential memory leak, when SDK is no longer needed
(when the app is closed, for example), a caller should call this method:

```
cfClient.close();
```

## Logger configuration

SDK uses Sl4j as facade to any MDC supported logging library like logback, log4j2 and others.
You probably already have log4j or logback in your project, 
and we provide variables for your configuration file.

Supported variables:
```
version - version of SDK, system property
flag - current flag evaluation, context variable
target - target used for evaluation, context variable
requestId - request identifer for interacting with FF backend, context variable
```

put Logback dependency inside dependencies tag in pom file 
```pom
    <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>1.2.10</version>
    </dependency>
```

sample logback configuration
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36}.%M - SDK=Java, Version=%property{version}, flag=%X{flag}, target=%X{target}, RequestID=%X{requestId} - %msg%n
            </pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```