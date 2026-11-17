#!/usr/bin/env bash
# Builds DrillPod.apk without Gradle (aapt / javac / d8-dx / zipalign / apksigner).
#
#   ./scripts/fetch-ruffle.sh          # once: downloads Ruffle into app/assets/www
#   KEYSTORE=my.jks KEYSTORE_PASS=... KEY_ALIAS=... ./build.sh [out.apk]
#
# Debian/Ubuntu tools: apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk
# The APK ships NO game content: players import their own copy of the game file.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/DrillPod.apk}"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX="${DX:-dalvik-exchange}"
: "${KEYSTORE:?set KEYSTORE to your signing keystore (never commit it)}"
: "${KEYSTORE_PASS:?set KEYSTORE_PASS}"
KEY_ALIAS="${KEY_ALIAS:-drillpod}"
KEY_PASS="${KEY_PASS:-$KEYSTORE_PASS}"

[ -f "$HERE/app/assets/www/ruffle.js" ] || { echo "Ruffle is missing: run ./scripts/fetch-ruffle.sh first" >&2; exit 1; }

B="$(mktemp -d)"; trap 'rm -rf "$B"' EXIT
cp -r "$HERE/app" "$B/app"; P="$B/app"
rm -f "$P"/assets/www/*.swf
# The game only runs from its original web address: the app serves the bundled pages under that
# host name from local files (nothing is fetched from the network for it).
sed -i "s/@@HOST@@/www.xgenstudios.com/; s/@@SCHEME@@/http/" "$P/src/com/drillpod/launcher/MainActivity.java"
sed -i "s/@@CLEARTEXT@@/true/" "$P/AndroidManifest.xml"
mkdir -p "$B/gen" "$B/obj"
aapt package -f -0 arsc -M "$P/AndroidManifest.xml" -S "$P/res" -A "$P/assets" -I "$ANDROID_JAR" -J "$B/gen" -F "$B/unsigned.apk"
javac -nowarn --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$B/obj" $(find "$P/src" "$B/gen" -name '*.java')
"$DX" --dex --output="$B/classes.dex" "$B/obj"
(cd "$B" && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 "$B/unsigned.apk" "$B/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" --out "$OUT" "$B/aligned.apk"
apksigner verify "$OUT" && echo "OK -> $OUT"
