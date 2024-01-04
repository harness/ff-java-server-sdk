
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
or directly run the executable
```
build/native/nativeCompile/ffsdk-native
```


