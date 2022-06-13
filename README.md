Harness Feature Flag Java SDK
========================

## Table of Contents
**[Intro](#Intro)**<br>
**[Requirements](#Requirements)**<br>
**[Quickstart](#Quickstart)**<br>
**[Further Reading](docs/further_reading.md)**<br>


## Intro

Harness Feature Flags (FF) is a feature management solution that enables users to change the software’s functionality, without deploying new code. FF uses feature flags to hide code or behaviours without having to ship new versions of the software. A feature flag is like a powerful if statement.
* For more information, see https://harness.io/products/feature-flags/
* To read more, see https://ngdocs.harness.io/category/vjolt35atg-feature-flags
* To sign up, https://app.harness.io/auth/#/signup/

![FeatureFlags](https://github.com/harness/ff-java-server-sdk/raw/main/docs/images/ff-gui.png)

## Requirements

[JDK 8](https://openjdk.java.net/install/) or newer<br>
Maven or Gradle

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

## Quickstart
The Feature Flag SDK provides a client that connects to the feature flag service, and fetches the value
of feature flags. The following section provides an example of how to install the SDK and initialize it from an application.

This quickstart assumes you have followed the instructions to [setup a Feature Flag project and have created a flag called `harnessappdemodarkmode` and created a server API Key](https://ngdocs.harness.io/article/1j7pdkqh7j-create-a-feature-flag#step_1_create_a_project).

### Install the SDK

The first step is to install the FF SDK as a dependency in your application using your application's dependency manager. You can use Maven, Gradle, SBT, etc. for your application.

Refer to the [Harness Feature Flag Java Server SDK](https://mvnrepository.com/artifact/io.harness/ff-java-server-sdk) to identify the latest version for your build automation tool.

This section lists dependencies for Maven and Gradle and uses the 1.1.5.1 version as an example:

#### Maven

Add the following Maven dependency in your project's pom.xml file:
```pom
<dependency>
    <groupId>io.harness</groupId>
    <artifactId>ff-java-server-sdk</artifactId>
    <version>1.1.5.1</version>
</dependency>
```

#### Gradle

```
implementation group: 'io.harness', name: 'ff-java-server-sdk', version: '1.1.5.1'
```

### A Simple Example
Here is a complete example that will connect to the feature flag service and report the flag value every 10 seconds until the connection is closed.  
Any time a flag is toggled from the feature flag service you will receive the updated value.

After installing the SDK, enter the SDK keys that you created for your environment. The SDK keys authorize your application to connect to the FF client. 

```java
package io.harness.ff.examples;

import io.harness.cf.client.api.*;
import io.harness.cf.client.connector.HarnessConnector;
import io.harness.cf.client.dto.Target;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GettingStarted {
    // API Key - set this as an env variable
    private static String apiKey = getEnvOrDefault("FF_API_KEY", "");

    // Flag Identifier
    private static String flagName = getEnvOrDefault("FF_FLAG_NAME", "harnessappdemodarkmode");

    public static void main(String[] args) {
        log.info("Harness SDK Getting Started");

        try {
            // Create a Feature Flag Client
            CfClient cfClient = new CfClient(apiKey);
            cfClient.waitForInitialization();

            // Create a target (different targets can get different results based on rules.  This includes a custom attribute 'location')
            final Target target = Target.builder()
                    .identifier("javasdk")
                    .name("JavaSDK")
                    .attribute("location", "emea")
                    .build();
            
            // Loop forever reporting the state of the flag
            while (true) {
                boolean result = cfClient.boolVariation(flagName, target, false);
                log.info("Flag variation " +result);
                Thread.sleep(10000);
            }
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

### Running with docker
If you don't have the right version of python installed locally, or don't want to install the dependencies you can
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
