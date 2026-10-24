#!/usr/bin/env bash
set -euo pipefail
serial=$(adb get-serialno)
case "$serial" in emulator-*) ;; *) echo 'Refusing to remove packages from a non-emulator device'; exit 1 ;; esac
package=cn.ziyis.cqustwifi
current=(artifacts/release-current/*.apk)
previous=(artifacts/release-previous/*.apk)
test "${#current[@]}" -eq 1 && test -f "${current[0]}"
exec > >(tee artifacts/release-install.txt) 2>&1
adb -s "$serial" uninstall "$package.test" || true
adb -s "$serial" uninstall "$package"
upgrading=false
if adb -s "$serial" install --no-streaming "${previous[0]}"; then
  echo 'Previous v3-only release accepted by stock Android package manager.'
  adb -s "$serial" shell am start -W -n "$package/.MainActivity"
  adb -s "$serial" shell mkdir -p "/sdcard/Android/data/$package/files"
  adb -s "$serial" shell touch "/sdcard/Android/data/$package/files/upgrade-marker"
  upgrading=true
fi
adb -s "$serial" install --no-streaming -r "${current[0]}"
adb -s "$serial" shell dumpsys package "$package" > artifacts/release-package.txt
grep -q 'versionCode=4' artifacts/release-package.txt
grep -q 'versionName=1.1.0-beta02' artifacts/release-package.txt
if [ "$upgrading" = true ]; then adb -s "$serial" shell test -f "/sdcard/Android/data/$package/files/upgrade-marker"; fi
adb -s "$serial" shell am force-stop "$package"
adb -s "$serial" shell am start -W -n "$package/.MainActivity"
sleep 3
adb -s "$serial" shell pidof "$package"
adb -s "$serial" shell uiautomator dump /sdcard/release-window.xml
adb -s "$serial" pull /sdcard/release-window.xml artifacts/release-window.xml
grep -q 'CQUST Wi-Fi' artifacts/release-window.xml
mkdir -p artifacts/screenshots
adb -s "$serial" exec-out screencap -p > artifacts/screenshots/14-signed-release.png
echo 'SIGNED_RELEASE_INSTALL_LAUNCH_AND_UPGRADE_OK'
