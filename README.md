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

### Install the FF SDK Dependency

The first step is to install the FF SDK as a dependency in your application using your application's dependency manager. You can use Maven, Gradle, SBT, etc. for your application.

Refer to the [Harness Feature Flag Java Server SDK](https://mvnrepository.com/artifact/io.harness/ff-java-server-sdk) to identify the latest version for your build automation tool.

This section lists dependencies for Maven and Gradle and uses the 1.1.5.2 version as an example:

#### Maven

Add the following Maven dependency in your project's pom.xml file:
```pom
<dependency>
    <groupId>io.harness</groupId>
    <artifactId>ff-java-server-sdk</artifactId>
    <version>1.1.5.2</version>
</dependency>
```

#### Gradle

```
implementation group: 'io.harness', name: 'ff-java-server-sdk', version: '1.1.5.2'
```

### A Simple Example

After installing the SDK, enter the SDK keys that you created for your environment. The SDK keys authorize your application to connect to the FF client. All features of the Java SDK are provided by the base class called `CfClient`.

```java
public class SimpleExample {
    public static void main(String[] args) {
        try {
            /**
             * Put the API Key here from your environment
             * Initialize the CfClient
             */
            String apiKey = System.getProperty("FF_API_KEY", "<default api key>");
            String flagName = System.getProperty("FF_FLAG_NAME", "<default flag name>");
            
            CfClient cfClient = new CfClient(apiKey, Config.builder().build());
            cfClient.waitForInitialization();
    
            while(true) {
                /**
                 * Sleep for sometime before printing the value of the flag
                 */
                Thread.sleep(2000);
                /**
                 * This is a sample boolean flag. You can replace the flag value with
                 * the identifier of your feature flag
                 */
                boolean result = cfClient.boolVariation(flagName, target, <default value>);
                log.info("Boolean variation is " + result);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
```


### Additional Reading

Further examples and config options are in the further reading section:

[Further Reading](docs/further_reading.md)


-------------------------
[Harness](https://www.harness.io/) is a feature management platform that helps teams to build better software and to
test features quicker.

-------------------------
