#!/usr/bin/env bash
set -euo pipefail
for attempt in $(seq 1 60); do
  if adb shell service check phone | grep -q ': found'; then exit 0; fi
  sleep 1
done
echo 'Android telephony service did not become ready'
exit 1
