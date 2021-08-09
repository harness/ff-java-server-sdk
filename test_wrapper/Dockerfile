FROM fedora:34

ARG PORT
ARG API_KEY
ARG SELF_TEST
ARG WRAPPERS_BRANCH

ENV PORT "$PORT"
ENV API_KEY "$API_KEY"
ENV SELF_TEST "$SELF_TEST"
ENV WRAPPERS_BRANCH "$WRAPPERS_BRANCH"

RUN dnf group install -y "Development Tools" && \
    sudo dnf install -y https://mirrors.rpmfusion.org/free/fedora/rpmfusion-free-release-$(rpm -E %fedora).noarch.rpm \
        https://mirrors.rpmfusion.org/nonfree/fedora/rpmfusion-nonfree-release-$(rpm -E %fedora).noarch.rpm && \
    dnf install -y wget java java-devel maven && \
    cd / && wget "https://downloads.gradle-dn.com/distributions/gradle-7.0-bin.zip" && \
    unzip "gradle-7.0-bin.zip"

RUN mkdir CFJavaSDK && cd ./CFJavaSDK && \
    git clone --recurse-submodules https://github.com/drone/ff-java-server-sdk.git . && \
    echo "Wrappers branch: $WRAPPERS_BRANCH" && \
    git checkout "$WRAPPERS_BRANCH" && \
    git submodule init && git submodule update && \
    echo "{\"selfTest\": $SELF_TEST, \"port\": $PORT, \"apiKey\": \"$API_KEY\", \"logger\": \"filesystem\"}" \
        > "test_wrapper/wrapper/wrapper.json" && \
        echo "wrapper.json:" && cat "test_wrapper/wrapper/wrapper.json" && \
    cd "test_wrapper" && \
    ../../gradle-7.0/bin/gradle wrapper --gradle-version 7.0 && ../../gradle-7.0/bin/gradle --stop && \
    mkdir -p "/root/.m2"

COPY maven.settings.xml /root/.m2/settings.xml

# TODO: Populate settings.xml with parameters

EXPOSE $PORT

CMD cd ./CFJavaSDK && git fetch && git pull && \
    echo "Building and installing FF Java Server SDK" && \
    mvn clean && mvn compile && mvn package && mvn install && cd "test_wrapper" && \
    echo "Preparing the build" && \
    ./gradlew --stop && ./gradlew clean --stacktrace && \
    echo "Assembling the build" && \
    ./gradlew wrapper:assemble --stacktrace && \
    echo "Running" && \
    ./gradlew wrapper:test --stacktrace
