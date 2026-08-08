#!/bin/sh
# Gradle wrapper script placeholder
# 在实际项目中，运行: gradle wrapper --gradle-version 8.5
# 会自动生成 gradlew 和 gradle-wrapper.jar

if [ -f ./gradle/wrapper/gradle-wrapper.jar ]; then
    exec java -classpath ./gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$@"
else
    echo "gradle-wrapper.jar not found. Running gradle directly..."
    exec gradle "$@"
fi
