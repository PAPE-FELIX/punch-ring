#!/bin/zsh
set -euo pipefail
ROOT_DIR="${0:A:h:h}"
SDK_DIR=/opt/homebrew/share/android-commandlinetools
BUILD_TOOLS="$SDK_DIR/build-tools/36.0.0"
PLATFORM_JAR="$SDK_DIR/platforms/android-36/android.jar"
TEST_DIR="$ROOT_DIR/.build/icon-render"
mkdir -p "$TEST_DIR/classes" "$TEST_DIR/dex" "$ROOT_DIR/dist/icon-preview"
"$BUILD_TOOLS/aapt2" link -I "$PLATFORM_JAR" --manifest "$ROOT_DIR/tests/render/AndroidManifest.xml" -o "$TEST_DIR/unsigned.apk"
javac --release 17 -classpath "$PLATFORM_JAR" -d "$TEST_DIR/classes" \
  "$ROOT_DIR/android-app/src/com/pape/punchring/"{PunchRingView,AppSettings,CutoutGeometry,StatusState,StatusHaptic,StatusIconMotion,StatusIconGlyph,StatusDroplet,GenieMesh,GenieTransition,RingStateMotion,BatteryFillMotion,MotionClock,VpnIndicator}.java \
  "$ROOT_DIR/tests/render/IconRenderTest.java"
"$BUILD_TOOLS/d8" --lib "$PLATFORM_JAR" --min-api 31 --output "$TEST_DIR/dex" "$TEST_DIR"/classes/com/pape/punchring/*.class
(cd "$TEST_DIR/dex" && zip -q -u "$TEST_DIR/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$TEST_DIR/unsigned.apk" "$TEST_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" sign --ks "$ROOT_DIR/.build/punch-ring-debug.keystore" --ks-key-alias punchring \
  --ks-pass pass:punchring --key-pass pass:punchring --out "$TEST_DIR/test.apk" "$TEST_DIR/aligned.apk"
adb -s "$1" install -r "$TEST_DIR/test.apk"
adb -s "$1" shell am instrument -w com.pape.punchring.rendertest/com.pape.punchring.IconRenderTest
for name in icon-sheet icon-before icon-hold icon-return icon-after genie-phases spin-peak dots-transition droplet-phases fast-charge-mint icon-size-5x vpn-corners; do
  adb -s "$1" exec-out run-as com.pape.punchring.rendertest cat "files/$name.png" > "$ROOT_DIR/dist/icon-preview/$name.png"
done
adb -s "$1" uninstall com.pape.punchring.rendertest
