#!/usr/bin/env sh
set -e
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$DIR"
exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
