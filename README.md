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
    <version>0.0.9.1</version>
</dependency>
```

After dependency has been added, the SDK elements, primarily `CfClient` should be accessible in the main application.

## Initialization

`CfClient` is a base class that provides all features of SDK.

```
/**
 * Put the API Key here from your environment
 */
String apiKey = "YOUR_API_KEY";

CfClient cfClient = new CfClient(apiKey, Config.builder().build());

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

* `public boolean boolVariation(String key, Target target, boolean defaultValue)`

* `public String stringVariation(String key, Target target, String defaultValue)`

* `public double numberVariation(String key, Target target, int defaultValue)`

* `public JsonObject jsonVariation(String key, Target target, JsonObject defaultValue)`

* `public void destroy()`

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
boolean result = cfClient.numberVariation("sample_number_flag", target, 0);  
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

## Shutting down the SDK

To avoid potential memory leak, when SDK is no longer needed
(when the app is closed, for example), a caller should call this method:

```Kotlin
cfClient.destroy();
```

