#!/usr/bin/env bash
# Builds the sideloadable arm64 debug APK. Tuned for low-memory (2GB) machines:
# single worker, no daemon reuse, serial GC. On a normal dev machine just run
# `gradle :app:assembleDebug` instead.
set -euo pipefail
D=~/.cache/devtools
export JAVA_HOME="$D/jdk17" ANDROID_HOME="$D/android-sdk" GRADLE_USER_HOME="$D/gradle-home"
export PATH="$JAVA_HOME/bin:$D/gradle/bin:$PATH"
cd "$(dirname "$0")/.."
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle :app:assembleDebug --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1000m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC" \
  -Dorg.gradle.vfs.watch=false
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
