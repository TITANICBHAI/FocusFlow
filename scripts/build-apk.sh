#!/usr/bin/env bash

set -euo pipefail

echo "== FocusFlow APK build =="
echo "Started: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"

chmod +x ./gradlew
./gradlew :app:assembleProductionDebug --no-daemon --console=plain --stacktrace

apk_path="app/build/outputs/apk/production/debug/app-production-debug.apk"
if [[ ! -f "$apk_path" ]]; then
  echo "ERROR: Expected APK was not produced at $apk_path" >&2
  exit 1
fi

echo "APK: $apk_path"
sha256sum "$apk_path"
echo "Finished: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"