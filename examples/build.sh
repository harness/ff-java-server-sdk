#!/bin/bash


./gradlew clean gettingStarted --refresh-dependencies

jar tvf build/libs/ff-java-sdk-sample-demo-app-1.0-SNAPSHOT.jar | grep harness
java -jar build/libs/ff-java-sdk-sample-demo-app-1.0-SNAPSHOT.jar
