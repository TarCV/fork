#!/usr/bin/env bash

set -ex

cd `dirname "$0"`

export GRADLE_OPTS="-Dorg.gradle.logging.level=quiet -Dorg.gradle.console=plain"
rm *.log || true
./gradlew :app:fork
./gradlew :app:test
