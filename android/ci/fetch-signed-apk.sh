#!/usr/bin/env bash
set -euo pipefail
mkdir -p artifacts/release-current
target=artifacts/release-current/CQUST-WiFi-Android-1.1.0-beta02.apk
if [ -n "${SIGNED_APK_BLOB:-}" ]; then
  [[ "$SIGNED_APK_BLOB" =~ ^[0-9a-f]{40}(,[0-9a-f]{40}){0,63}$ ]] && [[ "$SIGNED_APK_SHA256" =~ ^[0-9a-f]{64}$ ]]
  : > "$target"
  IFS=',' read -ra chunks <<< "$SIGNED_APK_BLOB"
  for chunk in "${chunks[@]}"; do
    gh api --allow-escape-sequences -H 'Accept: application/vnd.github.raw+json' "repos/$GITHUB_REPOSITORY/git/blobs/$chunk" >> "$target"
  done
  printf '%s  %s\n' "$SIGNED_APK_SHA256" "$target" | sha256sum --check --strict
else
  gh release download "$RELEASE_TAG" --repo "$GITHUB_REPOSITORY" --pattern 'CQUST-WiFi-Android-*.apk' --dir artifacts/release-current
fi
gh release download android-v1.1.0-beta01 --repo "$GITHUB_REPOSITORY" --pattern 'CQUST-WiFi-Android-*.apk' --dir artifacts/release-previous
