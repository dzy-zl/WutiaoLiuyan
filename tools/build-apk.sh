#!/usr/bin/env sh
set -e
cd "$(dirname "$0")/.."
./gradlew --no-daemon assembleDebug --stacktrace
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/WutiaoLiuyan-debug.apk
echo "APK: $(pwd)/dist/WutiaoLiuyan-debug.apk"
