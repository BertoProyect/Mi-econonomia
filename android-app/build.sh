#!/usr/bin/env bash
# Compila el APK firmado. Lo usa GitHub Actions; no hace falta tocarlo.
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${VERSION:-1}"
APP_URL="${APP_URL%/}/"
npm install --no-audit --no-fund
APP_URL="$APP_URL" VERSION="$VERSION" node make-config.js
rm -rf android
npx cap add android
npx cap sync android
for d in android/app/src/main/res/mipmap-*; do
  [ -f "$d/ic_launcher.png" ] && cp ../icons/icon-512.png "$d/ic_launcher.png"
  [ -f "$d/ic_launcher_round.png" ] && cp ../icons/icon-512.png "$d/ic_launcher_round.png"
  [ -f "$d/ic_launcher_foreground.png" ] && cp ../icons/maskable-512.png "$d/ic_launcher_foreground.png"
done
sed -i 's/#FFFFFF/#000000/I' android/app/src/main/res/values/ic_launcher_background.xml || true
PKG=android/app/src/main/java/com/bertoproject/mieconomia
mkdir -p "$PKG" android/app/src/main/res/drawable android/app/src/main/res/values
for f in native/*.java; do sed -e "s#__APP_URL__#${APP_URL}#g" -e "s#__VERSION__#${VERSION}#g" "$f" > "$PKG/$(basename "$f")"; done
cp native/res/drawable/*.xml android/app/src/main/res/drawable/
cp native/res/values/*.xml android/app/src/main/res/values/
node patch-android.js
sed -i -E "s/versionCode [0-9]+/versionCode ${VERSION}/; s/versionName \"[^\"]*\"/versionName \"1.${VERSION}\"/" android/app/build.gradle
grep -E "versionCode|versionName" android/app/build.gradle
cd android
chmod +x gradlew
if [ -n "${KS_B64:-}" ] && [ -n "${KS_PASS:-}" ]; then
  echo "$KS_B64" | base64 -d > ../release.keystore
  ./gradlew assembleRelease --no-daemon --stacktrace \
    -Pandroid.injected.signing.store.file="$(cd .. && pwd)/release.keystore" \
    -Pandroid.injected.signing.store.password="$KS_PASS" \
    -Pandroid.injected.signing.key.alias=mieconomia \
    -Pandroid.injected.signing.key.password="$KS_PASS"
  rm -f ../release.keystore
  cp app/build/outputs/apk/release/app-release.apk ../mi-economia.apk
  echo "FIRMA=propia"
else
  echo "Sin secretos de firma: APK con firma temporal (para actualizar habrá que desinstalar antes)"
  ./gradlew assembleDebug --no-daemon --stacktrace
  cp app/build/outputs/apk/debug/app-debug.apk ../mi-economia.apk
  echo "FIRMA=temporal"
fi
ls -la ../mi-economia.apk
