#!/usr/bin/env bash
# SayerTV Mobile — one-shot build environment bootstrap (Linux x64, no root needed)
# Installs JDK 17, Gradle 8.11.1 and the Android SDK into ~/.cache/devtools.
set -euo pipefail
D=~/.cache/devtools
mkdir -p "$D" && cd "$D"
[ -d jdk17 ]  || { curl -sL -o jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"; tar xzf jdk17.tar.gz; mv jdk-17* jdk17; }
[ -d gradle ] || { curl -sL -o gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"; unzip -q gradle.zip; mv gradle-8.11.1 gradle; }
if [ ! -d android-sdk/platforms/android-36 ]; then
  curl -sL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p android-sdk/cmdline-tools && unzip -q -o cmdtools.zip -d android-sdk/cmdline-tools
  [ -d android-sdk/cmdline-tools/latest ] || mv android-sdk/cmdline-tools/cmdline-tools android-sdk/cmdline-tools/latest
  export JAVA_HOME="$D/jdk17"
  yes | android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null 2>&1 || true
  android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools" >/dev/null
fi
echo "Build environment ready at $D"
