#!/bin/zsh
set -euo pipefail

ROOT_DIR="${0:A:h:h}"
# SDK 경로는 기기마다 다르다 — 맥미니는 ~/Library/Android/sdk, 흰둥이는 brew commandlinetools (2026-09-16 맥미니 인계)
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK_DIR" ] || for c in "$HOME/Library/Android/sdk" "/opt/homebrew/share/android-commandlinetools"; do [ -d "$c" ] && SDK_DIR="$c" && break; done
BUILD_TOOLS="$SDK_DIR/build-tools/36.0.0"
PLATFORM_JAR="$SDK_DIR/platforms/android-36/android.jar"
WORK_DIR="$ROOT_DIR/.build/android"
OUT_DIR="$ROOT_DIR/dist"
KEYSTORE="$ROOT_DIR/.build/punch-ring-debug.keystore"
APK_PATH="$OUT_DIR/Punch-Ring-Android.apk"

mkdir -p "$WORK_DIR/classes" "$WORK_DIR/dex" "$OUT_DIR"
rm -rf "$WORK_DIR/classes" "$WORK_DIR/dex"
mkdir -p "$WORK_DIR/classes" "$WORK_DIR/dex"
rm -f "$WORK_DIR/resources.zip" "$WORK_DIR/unsigned.apk" "$WORK_DIR/aligned.apk"

"$BUILD_TOOLS/aapt2" compile \
  --dir "$ROOT_DIR/android-app/res" \
  -o "$WORK_DIR/resources.zip"

"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM_JAR" \
  --manifest "$ROOT_DIR/android-app/AndroidManifest.xml" \
  --min-sdk-version 31 \
  --target-sdk-version 36 \
  "$WORK_DIR/resources.zip" \
  -o "$WORK_DIR/unsigned.apk"

javac \
  --release 17 \
  -Xlint:all,-deprecation,-options \
  -classpath "$PLATFORM_JAR" \
  -d "$WORK_DIR/classes" \
  "$ROOT_DIR"/android-app/src/com/pape/punchring/*.java

"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM_JAR" \
  --min-api 31 \
  --output "$WORK_DIR/dex" \
  "$WORK_DIR"/classes/com/pape/punchring/*.class

cd "$WORK_DIR/dex"
zip -q -u "$WORK_DIR/unsigned.apk" classes.dex

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass punchring \
    -keypass punchring \
    -alias punchring \
    -keyalg RSA \
    -keysize 2048 \
    -validity 3650 \
    -dname "CN=PAPE Punch Ring Development"
fi

"$BUILD_TOOLS/zipalign" -f 4 "$WORK_DIR/unsigned.apk" "$WORK_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias punchring \
  --ks-pass pass:punchring \
  --key-pass pass:punchring \
  --out "$APK_PATH" \
  "$WORK_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$APK_PATH"
echo "$APK_PATH"
