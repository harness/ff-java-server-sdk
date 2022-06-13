# Further Reading

Covers advanced topics (different config options and scenarios)

## Configuration Options
The following configuration options are available to control the behaviour of the SDK.
You can configure the URLs used to connect to Harness via the Connector config.
The reast of the configuration is part of the BaseConfig.

```java
// Connector Config
HarnessConfig connectorConfig = HarnessConfig.builder()
        .configUrl("https://config.ff.harness.io/api/1.0")
        .eventUrl("https://config.ff.harness.io/api/1.0")
        .build();

// Create Options
BaseConfig options = BaseConfig.builder()
        .pollIntervalInSeconds(60)
        .streamEnabled(true)
        .analyticsEnabled(true)
        .build();

// Create the client
CfClient cfClient = new CfClient(new HarnessConnector(apiKey, connectorConfig), options);
```


| Name            | Config Option                                                  | Description                                                                                                                                      | default                              |
|-----------------|----------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| baseUrl         | HarnessConfig.configUrl("https://config.ff.harness.io/api/1.0")        | the URL used to fetch feature flag evaluations. You should change this when using the Feature Flag proxy to http://localhost:7000                | https://config.ff.harness.io/api/1.0 |
| eventsUrl       | HarnessConfig.eventUrl("https://config.ff.harness.io/api/1.0"), | the URL used to post metrics data to the feature flag service. You should change this when using the Feature Flag proxy to http://localhost:7000 | https://events.ff.harness.io/api/1.0 |
| pollInterval    | BaseConfig.pollIntervalInSeconds(60))                                   | when running in stream mode, the interval in seconds that we poll for changes.                                                                   | 60                                   |
| enableStream    | BaseConfig.streamEnabled(false)                             | Enable streaming mode.                                                                                                                           | true                                 |
| enableAnalytics | BaseConfig.analyticsEnabled(true)                                                | Enable analytics.  Metrics data is posted every 60s                                                                                              | true                   |

## Logging Configuration
You can provide your own logger to the SDK and configure it using the standard logging configuration.
For example if using Log4j you can add the following log4j2.xml to your project to enable debug.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="INFO" monitorInterval="30">
    <Properties>
        <Property name="LOG_PATTERN">%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1} SDK=${sys:SDK} flag=${sys:version} target=%mdc{target} - %m%n</Property>
    </Properties>

    <Appenders>
        <Console name="console" target="SYSTEM_OUT" follow="true">
            <PatternLayout pattern="${LOG_PATTERN}"/>
        </Console>
    </Appenders>

    <Loggers>
        <Root level="debug">
            <AppenderRef ref="console"/>
        </Root>
    </Loggers>
</Configuration>

```


## Recommended reading

[Feature Flag Concepts](https://ngdocs.harness.io/article/7n9433hkc0-cf-feature-flag-overview)

[Feature Flag SDK Concepts](https://ngdocs.harness.io/article/rvqprvbq8f-client-side-and-server-side-sdks)

## Setting up your Feature Flags

[Feature Flags Getting Started](https://ngdocs.harness.io/article/0a2u2ppp8s-getting-started-with-feature-flags)

## Other Variation Types

### Bool Variation
```java
boolean result = cfClient.boolVariation("sample_boolean_flag", target, false);
```

### Number Variation
```java
boolean result = cfClient.numberVariation("sample_number_flag", target, 0);
```

### String Variation
```java
boolean result = cfClient.stringVariation("sample_string_flag", target, "");
```

### MultiVariate Examples
```java
double number = cfClient.numberVariation(COUNT_FEATURE_KEY, parentTarget, 1);
        String color = cfClient.stringVariation(COLOR_FEATURE_KEY, target, "black");
```
## Example log4j file

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" monitorInterval="30">
    <Properties>
        <Property name="LOG_PATTERN">%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1} SDK=${sys:SDK} flag=${sys:version} target=%mdc{target} - %m%n</Property>
    </Properties>

    <Appenders>
        <Console name="console" target="SYSTEM_OUT" follow="true">
            <PatternLayout pattern="${LOG_PATTERN}"/>
        </Console>
    </Appenders>

    <Loggers>
        <Root level="info">
            <AppenderRef ref="console"/>
        </Root>
    </Loggers>
</Configuration>
```

## Cleanup
To avoid potential memory leak, when SDK is no longer needed
(when the app is closed, for example), a caller should call this method:

```
cfClient.close();
```

## Change default URL

When using your Feature Flag SDKs with a [Harness Relay Proxy](https://ngdocs.harness.io/article/q0kvq8nd2o-relay-proxy) you need to change the default URL.


