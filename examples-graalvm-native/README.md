
## GraalVM

For Java SDK 1.5.0 experimental GraalVM support has been added. The steps below show you how to run the agent `-Pagent` to collect metadata on
codepaths within the SDK that are using reflection. You can then use the `metadataCopy` and merge the SDK metadata into the metadata for your application.

## Install GraalVM

[See www.graalvm.org/jdk21/docs/getting-started](https://www.graalvm.org/jdk21/docs/getting-started/)

## Setup native image support
```bash
gu install native-image
```

## Goto example
```bash
cd examples-graalvm-native
```

## Build reflection metadata for the SDK and its dependencies
Let this run to completion
```bash
./gradlew run -Pagent
```

## Copy the meta-data
```bash
./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image
```

## Run it

```bash
./gradlew nativeRun
```
Then you can directly run the executable
```
build/native/nativeCompile/ffsdk-native
```


