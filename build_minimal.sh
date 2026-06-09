#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-/usr/lib/android-sdk/build-tools/debian}"
OUT="$ROOT/build/minimal"
APK_DIR="$ROOT/build/apk"
KEYSTORE="$APK_DIR/debug.keystore"

if [ ! -f "$ANDROID_JAR" ]; then
  echo "Android jar not found: $ANDROID_JAR" >&2
  echo "Set ANDROID_JAR to an installed android.jar path." >&2
  exit 1
fi

for tool in aapt dx zipalign apksigner; do
  if [ ! -x "$BUILD_TOOLS/$tool" ]; then
    echo "Android build tool not found: $BUILD_TOOLS/$tool" >&2
    echo "Set BUILD_TOOLS to a build-tools directory containing $tool." >&2
    exit 1
  fi
done

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$APK_DIR"

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/aapt" package -f -m \
  -J "$OUT/gen" \
  -M "$ROOT/app/src/main/AndroidManifest.xml" \
  -S "$ROOT/app/src/main/res" \
  -I "$ANDROID_JAR"

javac -source 1.7 -target 1.7 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $(find "$OUT/gen" "$ROOT/app/src/main/java" -name '*.java' | sort)

"$BUILD_TOOLS/dx" --dex --output="$OUT/dex/classes.dex" "$OUT/classes"

"$BUILD_TOOLS/aapt" package -f \
  -M "$ROOT/app/src/main/AndroidManifest.xml" \
  -S "$ROOT/app/src/main/res" \
  -I "$ANDROID_JAR" \
  -F "$APK_DIR/poersmart-car-key-unsigned.apk"

(cd "$OUT/dex" && "$BUILD_TOOLS/aapt" add "$APK_DIR/poersmart-car-key-unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$APK_DIR/poersmart-car-key-unsigned.apk" "$APK_DIR/poersmart-car-key-aligned.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_DIR/poersmart-car-key.apk" \
  "$APK_DIR/poersmart-car-key-aligned.apk"

"$BUILD_TOOLS/apksigner" verify "$APK_DIR/poersmart-car-key.apk"
sha256sum "$APK_DIR/poersmart-car-key.apk" > "$APK_DIR/poersmart-car-key.apk.sha256"
echo "$APK_DIR/poersmart-car-key.apk"
