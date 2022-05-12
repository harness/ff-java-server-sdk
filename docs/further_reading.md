# Further Reading

Covers advanced topics (different config options and scenarios)

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


