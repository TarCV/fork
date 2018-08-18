#!/usr/bin/env bash

set -ex

cd `dirname "$0"`

GRADLE_OPTS="-Dorg.gradle.logging.level=quiet -Dorg.gradle.console=plain"
export GRADLE_OPTS
rm *.log || true
./gradlew app:compileDebugAndroidTestSources
./gradlew --info :app:fork
./gradlew app:compileDebugUnitTestSources
./gradlew --info :app:test
