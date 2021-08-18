FROM fedora:34

ARG PORT
ARG SELF_TEST
ARG WRAPPERS_BRANCH
ARG HARNESS_JFROG_INT_USR
ARG HARNESS_JFROG_INT_PWD

ENV PORT "$PORT"
ENV SELF_TEST "$SELF_TEST"
ENV WRAPPERS_BRANCH "$WRAPPERS_BRANCH"
ENV HARNESS_JFROG_INT_USR "$HARNESS_JFROG_INT_USR"
ENV HARNESS_JFROG_INT_PWD "$HARNESS_JFROG_INT_PWD"

RUN dnf group install -y "Development Tools" && \
    sudo dnf install -y https://mirrors.rpmfusion.org/free/fedora/rpmfusion-free-release-$(rpm -E %fedora).noarch.rpm \
        https://mirrors.rpmfusion.org/nonfree/fedora/rpmfusion-nonfree-release-$(rpm -E %fedora).noarch.rpm && \
    dnf install -y wget java java-devel maven gnupg pinentry && \
    cd / && wget "https://downloads.gradle-dn.com/distributions/gradle-7.0-bin.zip" && \
    unzip "gradle-7.0-bin.zip"

RUN mkdir CFJavaSDK && cd ./CFJavaSDK && \
    git clone --recurse-submodules https://github.com/drone/ff-java-server-sdk.git . && \
    echo "Wrappers branch: $WRAPPERS_BRANCH" && \
    git checkout "$WRAPPERS_BRANCH" && \
    git submodule init && git submodule update && \
    cd "test_wrapper" && \
    ../../gradle-7.0/bin/gradle wrapper --gradle-version 7.0 && ../../gradle-7.0/bin/gradle --stop

COPY generate_maven_settings.sh /root/generate_maven_settings.sh

RUN gpg --batch --passphrase '' --quick-gen-key sdk@harness.io default default && \
    gpg --list-keys > /root/gpg_out_keys && \
    head -4 /root/gpg_out_keys | tail -1 | xargs | tail -c 9 > /root/gpg_out_key && \
    export GPG_KEY=$(cat /root/gpg_out_key) && \
    gpg --export-secret-keys "$GPG_KEY" > "$GPG_KEY.gpg" && \
    sh /root/generate_maven_settings.sh && \
    echo "/root/.m2/settings.xml:" && cat /root/.m2/settings.xml

EXPOSE $PORT

CMD export GPG_KEY=$(cat /root/gpg_out_key) && echo "GPG key: $GPG_KEY" && \
    cd ./CFJavaSDK && git fetch && git pull && \
    echo "{\"selfTest\": $SELF_TEST, \"port\": $PORT, \"sdkKey\": \"$SDK_KEY\", \"logger\": \"filesystem\"}" \
    > "test_wrapper/wrapper/wrapper.json" && \
    echo "wrapper.json:" && cat "test_wrapper/wrapper/wrapper.json" && \
    echo "Building and installing FF Java Server SDK" && \
    mvn clean && mvn compile && mvn package -DskipTests=true && \
    mvn install -DskipTests=true && cd "test_wrapper" && \
    echo "Preparing the build" && \
    ./gradlew --stop && ./gradlew clean --stacktrace && \
    echo "Assembling the build" && \
    ./gradlew wrapper:assemble --stacktrace && \
    echo "Running" && \
    ./gradlew wrapper:test --stacktrace

