Java SDK For Harness Feature Flags
========================

## Table of Contents
**[Intro](#Intro)**<br>
**[Requirements](#Requirements)**<br>
**[Quickstart](#Quickstart)**<br>
**[Further Reading](docs/further_reading.md)**<br>


## Intro

Use this README to get started with our Feature Flags (FF) SDK for Java. This guide outlines the basics of getting started with the SDK and provides a full code sample for you to try out.
This sample doesn’t include configuration options, for in depth steps and configuring the SDK, for example, disabling streaming or using our Relay Proxy, see the  [Java SDK Reference](https://ngdocs.harness.io/article/i7et9ebkst-integrate-feature-flag-with-java-sdk).

For a sample FF Java SDK project, see our [test Java project](https://github.com/harness/ff-java-server-sdk/blob/main/examples/src/main/java/io/harness/ff/examples/GettingStarted.java).


![FeatureFlags](https://github.com/harness/ff-java-server-sdk/raw/main/docs/images/ff-gui.png)

## Requirements

To use this SDK, make sure you've:

- Installed[JDK 8](https://openjdk.java.net/install/) or a newer version<br>
- Installed Maven or Gradle or an alternative build automation tool for your application

To follow along with our test code sample, make sure you’ve:
- [Created a Feature Flag](https://ngdocs.harness.io/article/1j7pdkqh7j-create-a-feature-flag) on the Harness Platform called harnessappdemodarkmode
- Created a [server SDK key](https://ngdocs.harness.io/article/1j7pdkqh7j-create-a-feature-flag#step_3_create_an_sdk_key) and made a copy of it

### General Dependencies

[Defined in the main project](./pom.xml)

### Logging Dependencies

Logback
```pom
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>VERSION</version>
</dependency>
```

Log4j
```pom
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>VERSION</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j-impl</artifactId>
    <version>VERSION</version>
</dependency>
```

### Install the SDK

The first step is to install the FF SDK as a dependency in your application using your application's dependency manager. You can use Maven, Gradle, SBT, etc. for your application.

Refer to the [Harness Feature Flag Java Server SDK](https://mvnrepository.com/artifact/io.harness/ff-java-server-sdk) to identify the latest version for your build automation tool.

This section lists dependencies for Maven and Gradle and uses the 1.1.5.3 version as an example:

#### Maven

Add the following Maven dependency in your project's pom.xml file:
```pom
<dependency>
    <groupId>io.harness</groupId>
    <artifactId>ff-java-server-sdk</artifactId>
    <version>1.1.5.3</version>
</dependency>
```

#### Gradle

```
implementation group: 'io.harness', name: 'ff-java-server-sdk', version: '1.1.5.3'

```

### Code Sample
Here is a complete example that will connect to the feature flag service and report the flag value every 10 seconds until the connection is closed.
Any time a flag is toggled from the feature flag service you will receive the updated value.

After installing the SDK, enter the SDK keys that you created for your environment. The SDK keys authorize your application to connect to the FF client.

```java
package io.harness.ff.examples;

import io.harness.cf.client.api.*;
import io.harness.cf.client.dto.Target;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GettingStarted {
    // API Key - set this as an env variable
    private static String apiKey = getEnvOrDefault("FF_API_KEY", "");

    // Flag Identifier
    private static String flagName = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        System.out.println("Harness SDK Getting Started");

        try {
            //Create a Feature Flag Client
            CfClient cfClient = new CfClient(apiKey);
            cfClient.waitForInitialization();

            // Create a target (different targets can get different results based on rules.  This includes a custom attribute 'location')
            final Target target = Target.builder()
                    .identifier("javasdk")
                    .name("JavaSDK")
                    .attribute("location", "emea")
                    .build();

            // Loop forever reporting the state of the flag
            scheduler.scheduleAtFixedRate(
                    () -> {
                        boolean result = cfClient.boolVariation(flagName, target, false);
                        System.out.println("Boolean variation is " + result);
                    },
                    0,
                    10,
                    TimeUnit.SECONDS);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Close the SDK
            CfClient.getInstance().close();
        }
    }

    // Get the value from the environment or return the default
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}
```

### Running the example

```bash
export FF_API_KEY=<your key here>
cd examples

mvn clean package
mvn exec:java -Dexec.mainClass="io.harness.ff.examples.GettingStarted"
```

### Running the example with Docker
If you don't have the right version of java installed locally, or don't want to install the dependencies you can
use docker to get started.

```bash
# Clean and Package
docker run -v $(PWD)/examples:/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.3-jdk-8 mvn clean package

# Run the Example
docker run -e FF_API_KEY=$FF_API_KEY -v $(PWD)/examples:/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.3-jdk-8 mvn exec:java -Dexec.mainClass="io.harness.ff.examples.GettingStarted"
```


### Additional Reading

Further examples and config options are in the further reading section:

[Further Reading](docs/further_reading.md)


-------------------------
[Harness](https://www.harness.io/) is a feature management platform that helps teams to build better software and to
test features quicker.

-------------------------
