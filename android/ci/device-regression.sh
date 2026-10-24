#!/usr/bin/env bash
set -euo pipefail
mkdir -p artifacts
python3 app/src/androidTest/fixtures/socks_fixture.py > artifacts/socks-fixture.log 2>&1 &
fixture_pid=$!
collect() {
  adb pull /sdcard/Android/data/cn.ziyis.cqustwifi/files/screenshots artifacts/screenshots || true
  adb logcat -d -b crash > artifacts/emulator-crash.log || true
  kill "$fixture_pid" 2>/dev/null || true
}
trap collect EXIT
for attempt in $(seq 1 30); do
  kill -0 "$fixture_pid"
  if python3 -c 'import socket; socket.create_connection(("127.0.0.1",17890),1).close()' 2>/dev/null; then break; fi
  if [ "$attempt" = 30 ]; then cat artifacts/socks-fixture.log; exit 1; fi
  sleep 1
done

adb shell svc wifi disable
adb shell svc data disable
adb install --no-streaming -r vpnprobe/build/outputs/apk/debug/vpnprobe-debug.apk
if [ "${1:-35}" = "37" ]; then
  # Avoid split/streamed installation and another Gradle JVM alongside API 37's emulator.
  adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
  adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
  adb shell am instrument -w -r cn.ziyis.cqustwifi.test/androidx.test.runner.AndroidJUnitRunner | tee artifacts/instrumentation.txt
  grep -Eq '^OK \(14 tests\)' artifacts/instrumentation.txt
  if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=' artifacts/instrumentation.txt; then exit 1; fi
else
  gradle --no-daemon -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true :app:connectedDebugAndroidTest
fi
if [ -d artifacts/release-current ]; then
  collect
  bash ci/release-install-smoke.sh
fi
