#!/bin/sh

MVN_HOME="/root/.m2"
SETTINGS="$MVN_HOME/settings.xml"

mkdir -p "$MVN_HOME" && touch "$SETTINGS" &&
cat >> "$SETTINGS"<< EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                    https://maven.apache.org/xsd/settings-1.0.0.xsd">

    <servers>

        <server>
            <id>harness-jfrog-internal</id>
            <username>$HARNESS_JFROG_INT_USR</username>
            <password>$HARNESS_JFROG_INT_PWD</password>
            <configuration>
                <connectionTimeout>240000</connectionTimeout>
                <readTimeout>240000</readTimeout>
            </configuration>
        </server>

        <server>
            <id>harness</id>
            <configuration>
                <connectionTimeout>240000</connectionTimeout>
                <readTimeout>240000</readTimeout>
            </configuration>
        </server>
    </servers>

    <profiles>

        <profile>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <gpg.keyname>$GPG_KEY</gpg.keyname>
            </properties>
        </profile>
    </profiles>
</settings>
EOF
