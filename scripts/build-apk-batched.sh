#!/usr/bin/env bash
# SayerTV Mobile — MICRO-BATCHED build pipeline.
# Each stage is a separate short Gradle invocation with its own JVM, killed on exit.
# Designed for low-memory (2GB) CI/sandbox machines where a monolithic
# `assembleDebug` OOMs or exceeds command timeouts.
#
# Usage:  ./scripts/build-apk-batched.sh <stage>
# Stages: 1=core-a  2=core-b  3=features  4=app-compile  5=dex  6=package  all
set -euo pipefail
D=~/.cache/devtools
export JAVA_HOME="$D/jdk17" ANDROID_HOME="$D/android-sdk" GRADLE_USER_HOME="$D/gradle-home"
export PATH="$JAVA_HOME/bin:$D/gradle/bin:$PATH"
cd "$(dirname "$0")/.."
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Two memory profiles (gradle.properties is the only place Gradle reliably reads):
#  compile stages need metaspace (Kotlin compiler classes); dex stages need heap.
set_mem() { sed -i "s/^org.gradle.jvmargs=.*/org.gradle.jvmargs=$1 -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -Dfile.encoding=UTF-8/" gradle.properties; }
MEM_COMPILE="-Xmx860m -XX:MaxMetaspaceSize=460m"
MEM_DEX="-Xmx1050m -XX:MaxMetaspaceSize=280m"

GFLAGS=(--max-workers=1
  
  -Dorg.gradle.vfs.watch=false)

run() { echo ">>> STAGE: $*"; gradle "${GFLAGS[@]}" "$@"; pkill -9 -f gradle 2>/dev/null || true; sleep 1; }

stage=${1:-all}
set_mem "$MEM_COMPILE"
case "$stage" in
  1|all) run :core:common:compileDebugKotlin :core:database:compileDebugKotlin ;;&
  2|all) run :core:jellyfin:compileDebugKotlin :core:playback:compileDebugKotlin \
             :core:anilist:compileDebugKotlin :core:matching:compileDebugKotlin ;;&
  3|all) run :core:designsystem:compileDebugKotlin :feature:onboarding:compileDebugKotlin \
             :feature:library:compileDebugKotlin :feature:player:compileDebugKotlin ;;&
  4|all) run :app:compileDebugKotlin :app:hiltJavaCompileDebug ;;&
  5|all) set_mem "$MEM_DEX"
         run :app:dexBuilderDebug :app:mergeDebugGlobalSynthetics
         run :app:mergeExtDexDebug
         run :app:mergeLibDexDebug :app:mergeProjectDexDebug
         set_mem "$MEM_COMPILE" ;;&
  6|all) set_mem "$MEM_DEX"
         run :app:assembleDebug
         set_mem "$MEM_COMPILE"
         echo "APK: app/build/outputs/apk/debug/app-debug.apk" ;;
esac
