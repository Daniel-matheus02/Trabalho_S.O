#!/usr/bin/env sh
# Executa o servidor autoritativo autonomo.
set -e
cd "$(dirname "$0")"
./gradlew runServer --args="$*"
