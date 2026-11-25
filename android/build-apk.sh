#!/bin/bash
# Stages the native .so files and builds the debug APK.
# Run from the android/ directory (or anywhere; paths are relative to it).
# Uses the same env incantation as the Carbon build (see AGENTS.md):
# the Gradle daemon needs the proxy + user.home settings to talk to its
# own localhost socket.
set -e
cd "$(dirname "$0")"
REPO="$(dirname "$PWD")"
JNI_DIR="app/src/main/jniLibs/arm64-v8a"

mkdir -p "$JNI_DIR"
stage() {
  local src="$1" name
  name="$(basename "$src")"
  if [ ! -f "$src" ]; then
    echo "MISSING: $src (build the native targets first)" >&2
    exit 1
  fi
  cp -f "$src" "$JNI_DIR/$name"
  echo "staged $name ($(stat -c%s "$JNI_DIR/$name") bytes)"
}

stage "$REPO/build/android-arm64/libmain.so"
stage "$REPO/third_party/rexglue-sdk/out/linux-arm64/librexruntime.so"
stage "$REPO/build/android-arm64/librexgpu-xenos.so"
stage "$REPO/build/android-arm64/libtekken6helper.so"

echo "=== exports ==="
nm -D "$JNI_DIR/libmain.so" | grep -E " T SDL_main$" || { echo "libmain.so does NOT export SDL_main" >&2; exit 1; }
nm -D "$JNI_DIR/librexruntime.so" | grep " T JNI_OnLoad" || { echo "librexruntime.so does NOT export JNI_OnLoad" >&2; exit 1; }
echo "SDL_main + JNI_OnLoad OK"

export JAVA_HOME="$HOME/jdk17"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$HOME/gradle-8.7/bin:$JAVA_HOME/bin:$HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv6Addresses=true -Duser.home=$HOME"
export GRADLE_USER_HOME="$HOME/.gradle"
export GRADLE_OPTS="-Xmx2g -Djava.net.preferIPv6Addresses=true -Dhttps.proxyHost=localhost -Dhttps.proxyPort=18080 -Dhttp.proxyHost=localhost -Dhttp.proxyPort=18080 -Dhttps.nonProxyHosts=localhost|127.0.0.1 -Dhttp.nonProxyHosts=localhost|127.0.0.1"

"$HOME/gradle-8.7/bin/gradle" assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
echo "=== APK ==="
ls -la "$APK"
sha256sum "$APK"
