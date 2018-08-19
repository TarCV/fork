#!/usr/bin/env bash

set -ex

cd `dirname "$0"`

export GRADLE_OPTS="-Dorg.gradle.logging.level=quiet -Dorg.gradle.console=plain"
rm *.log || true
./gradlew app:compileDebugAndroidTestSources
./gradlew --info :app:fork
./gradlew app:compileDebugUnitTestSources
./gradlew --info :app:test
