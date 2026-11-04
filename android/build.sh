#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

ROOT="$(pwd)"

SDK="${ANDROID_SDK:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ] && [ -d "$ROOT/../android-sdk" ]; then
  SDK="$ROOT/../android-sdk"
fi
if [ -z "$SDK" ] || [ ! -d "$SDK" ]; then
  echo "ERROR: Android SDK not found. Set ANDROID_SDK or ANDROID_HOME." >&2
  exit 1
fi

BT_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
PLATFORM_VERSION="${PLATFORM_VERSION:-35}"
BT="$SDK/build-tools/$BT_VERSION"
PLATFORM="$SDK/platforms/android-$PLATFORM_VERSION/android.jar"

if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x "C:/Program Files/Android/Android Studio/jbr/bin/java.exe" ]; then
    JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
  else
    JAVA_HOME="$(dirname "$(dirname "$(command -v javac)")")"
  fi
fi

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) EXE=".exe" ;;
  *) EXE="" ;;
esac

JAVAC="$JAVA_HOME/bin/javac$EXE"
JAVA="$JAVA_HOME/bin/java$EXE"
KEYTOOL="$JAVA_HOME/bin/keytool$EXE"

if command -v python >/dev/null 2>&1; then
  PYTHON=python
else
  PYTHON=python3
fi

VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-1.0}"

if [ -f "$ROOT/keystore.properties" ]; then
  # Local release signing. Not committed; see .gitignore.
  set -a
  . "$ROOT/keystore.properties"
  set +a
fi

KEYSTORE="${KEYSTORE_PATH:-${storeFile:-$ROOT/yike.keystore}}"
KS_PASS="${KEYSTORE_PASSWORD:-${storePassword:-changeit}}"
KEY_ALIAS="${KEY_ALIAS:-${keyAlias:-yike}}"
KEY_PASS="${KEY_PASSWORD:-${keyPassword:-$KS_PASS}}"
OUT="${OUTPUT_APK:-build/yike.apk}"

rm -rf build
mkdir -p build/gen build/classes build/dex build/res

echo "[1/7] aapt2 compile"
"$BT/aapt2$EXE" compile --dir res -o build/res.zip

echo "[2/7] aapt2 link"
"$BT/aapt2$EXE" link \
  -o build/base.apk \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -R build/res.zip \
  --java build/gen \
  --min-sdk-version 26 \
  --target-sdk-version "$PLATFORM_VERSION" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "[3/7] javac"
find src build/gen -name '*.java' > build/sources.txt
"$JAVAC" -encoding UTF-8 --release 8 -classpath "$PLATFORM" -d build/classes @build/sources.txt

echo "[4/7] d8"
CLS=$(find build/classes -name '*.class')
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM" --min-api 26 --output build/dex $CLS

echo "[5/7] package dex"
"$PYTHON" add_dex.py build/base.apk build/dex/classes.dex build/unaligned.apk

echo "[6/7] zipalign"
"$BT/zipalign$EXE" -f 4 build/unaligned.apk build/aligned.apk

echo "[7/7] sign"
if [ ! -f "$KEYSTORE" ]; then
  if [ -n "${CI:-}" ]; then
    echo "ERROR: keystore missing in CI. Set KEYSTORE_PATH." >&2
    exit 1
  fi
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Yike, OU=App, O=Yike, L=Shenyang, ST=Liaoning, C=CN"
fi
"$JAVA" -jar "$BT/lib/apksigner.jar" sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out "$OUT" build/aligned.apk

"$BT/zipalign$EXE" -c -v 4 "$OUT" > /dev/null
ls -lh "$OUT"
echo "OK -> $ROOT/$OUT"
