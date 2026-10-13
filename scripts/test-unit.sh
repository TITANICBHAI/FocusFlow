#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

echo "== FocusFlow JVM unit tests =="
export FOCUSFLOW_SKIP_APK_BUILD=1
exec bash "$ROOT_DIR/scripts/build-apk-with-java.sh" \
  :app:testProductionDebugUnitTest \
  :app:testTbtechsdevDebugUnitTest \
  "$@"
