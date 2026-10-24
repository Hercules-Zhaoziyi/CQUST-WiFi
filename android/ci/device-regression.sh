#!/usr/bin/env bash
set -euo pipefail
mkdir -p artifacts
collect() {
  adb pull /sdcard/Android/data/cn.ziyis.cqustwifi/files/screenshots artifacts/screenshots || true
  adb logcat -d -b crash > artifacts/emulator-crash.log || true
}
trap collect EXIT

adb shell svc wifi disable
adb shell svc data disable
adb install --no-streaming -r vpnprobe/build/outputs/apk/debug/vpnprobe-debug.apk
if [ "${1:-35}" = "37" ]; then
  # Avoid split/streamed installation and another Gradle JVM alongside API 37's emulator.
  adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
  adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w -r cn.ziyis.cqustwifi.test/androidx.test.runner.AndroidJUnitRunner | tee artifacts/instrumentation.txt
  grep -Eq '^OK \(12 tests\)' artifacts/instrumentation.txt
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' artifacts/instrumentation.txt; then exit 1; fi
else
  gradle --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest
fi
if [ -d artifacts/release-current ]; then
  collect
  bash ci/release-install-smoke.sh
fi
