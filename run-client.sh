#!/usr/bin/env sh
# Executa o cliente desktop com o wrapper do Gradle.
set -e
cd "$(dirname "$0")"
./gradlew run
