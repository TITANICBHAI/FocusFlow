#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

FOCUSFLOW_SKIP_APK_BUILD="${FOCUSFLOW_SKIP_APK_BUILD:-0}"
case "$FOCUSFLOW_SKIP_APK_BUILD" in
  0|false|no|"")
    SKIP_APK_BUILD=0
    GRADLE_TASKS=(":app:assembleProductionDebug" "$@")
    ;;
  1|true|yes)
    SKIP_APK_BUILD=1
    if [[ "$#" -eq 0 ]]; then
      echo "ERROR: Provide at least one Gradle task when FOCUSFLOW_SKIP_APK_BUILD is enabled." >&2
      exit 1
    fi
    GRADLE_TASKS=("$@")
    ;;
  *)
    echo "ERROR: FOCUSFLOW_SKIP_APK_BUILD must be 0/false/no or 1/true/yes." >&2
    exit 1
    ;;
esac

JDK_DOWNLOAD_DIR=""
SDK_DOWNLOAD_DIR=""
cleanup() {
  if [[ -n "$JDK_DOWNLOAD_DIR" ]]; then
    rm -rf -- "$JDK_DOWNLOAD_DIR"
  fi
  if [[ -n "$SDK_DOWNLOAD_DIR" ]]; then
    rm -rf -- "$SDK_DOWNLOAD_DIR"
  fi
}
trap cleanup EXIT

if [[ "$SKIP_APK_BUILD" -eq 1 ]]; then
  echo "== FocusFlow Android Gradle task (JDK and Android SDK bootstrap) =="
else
  echo "== FocusFlow debug APK build (JDK and Android SDK bootstrap) =="
fi
echo "Started: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: This bootstrap script currently supports Linux only." >&2
  exit 1
fi

case "$(uname -m)" in
  x86_64|amd64) ADOPTIUM_ARCH="x64" ;;
  aarch64|arm64) ADOPTIUM_ARCH="aarch64" ;;
  *)
    echo "ERROR: Unsupported CPU architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

for tool in curl tar readlink unzip sha1sum yes; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "ERROR: Required tool '$tool' was not found in PATH." >&2
    exit 1
  fi
done

canonical_path() {
  local candidate="$1"
  if [[ "$candidate" != /* ]]; then
    candidate="$ROOT_DIR/$candidate"
  fi
  readlink -m -- "$candidate"
}

assert_path_outside_repo() {
  local candidate
  local label="$2"
  candidate="$(canonical_path "$1")"
  case "$candidate" in
    "$ROOT_DIR"|"$ROOT_DIR"/*)
      echo "ERROR: $label must be outside the Git repository." >&2
      echo "Refusing path: $candidate" >&2
      exit 1
      ;;
  esac
}

path_is_inside_repo() {
  local candidate
  candidate="$(canonical_path "$1")"
  [[ "$candidate" == "$ROOT_DIR" || "$candidate" == "$ROOT_DIR"/* ]]
}

assert_sdk_outside_repo() {
  assert_path_outside_repo "$1" "Android SDK paths"
}

java_major_version() {
  local java_bin="$1"
  "$java_bin" -XshowSettings:properties -version 2>&1 \
    | sed -nE 's/^[[:space:]]*java.specification.version = ([0-9]+).*$/\1/p' \
    | head -n 1
}

java_home_for() {
  local resolved_java
  resolved_java="$(readlink -f "$1")"
  cd -P "$(dirname "$resolved_java")/.." && pwd
}

JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
fi
if [[ -z "$JAVA_BIN" ]] || [[ "$(java_major_version "$JAVA_BIN")" != "17" ]]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
  fi
fi

if [[ -n "$JAVA_BIN" ]] && [[ "$(java_major_version "$JAVA_BIN")" == "17" ]]; then
  JAVA_HOME="$(java_home_for "$JAVA_BIN")"
  echo "Using existing JDK 17 at $JAVA_HOME"
else
  CACHE_BASE="${XDG_CACHE_HOME:-}"
  if [[ -z "$CACHE_BASE" ]]; then
    CACHE_BASE="${HOME:-}"
    if [[ -n "$CACHE_BASE" ]]; then
      CACHE_BASE="$CACHE_BASE/.cache"
    fi
  fi
  if [[ -z "$CACHE_BASE" ]]; then
    CACHE_BASE="${TMPDIR:-/tmp}"
  fi
  if path_is_inside_repo "$CACHE_BASE"; then
    if [[ -n "${HOME:-}" ]] && ! path_is_inside_repo "$HOME"; then
      CACHE_BASE="$HOME/.cache"
    else
      CACHE_BASE="/tmp"
    fi
  fi
  if path_is_inside_repo "$CACHE_BASE"; then
    echo "ERROR: Could not find a JDK cache directory outside the Git repository." >&2
    exit 1
  fi

  assert_path_outside_repo "$CACHE_BASE" "JDK cache paths"
  CACHE_DIR="$CACHE_BASE/focusflow/android-build"
  CACHED_JDK="$CACHE_DIR/jdk-17"
  mkdir -p "$CACHE_DIR"

  if [[ -x "$CACHED_JDK/bin/java" ]] && [[ "$(java_major_version "$CACHED_JDK/bin/java")" == "17" ]]; then
    JAVA_HOME="$CACHED_JDK"
    echo "Using cached JDK 17 at $JAVA_HOME"
  else
    for tool in mktemp find; do
      if ! command -v "$tool" >/dev/null 2>&1; then
        echo "ERROR: Required tool '$tool' was not found in PATH." >&2
        exit 1
      fi
    done

    JDK_DOWNLOAD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/focusflow-jdk17.XXXXXX")"
    mkdir -p "$JDK_DOWNLOAD_DIR/extracted"

    JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/${ADOPTIUM_ARCH}/jdk/hotspot/normal/eclipse"
    echo "No JDK 17 found; downloading one from Adoptium..."
    curl --fail --location --retry 3 --connect-timeout 20 \
      "$JDK_URL" --output "$JDK_DOWNLOAD_DIR/jdk-17.tar.gz"
    tar -xzf "$JDK_DOWNLOAD_DIR/jdk-17.tar.gz" -C "$JDK_DOWNLOAD_DIR/extracted"

    EXTRACTED_JDK="$(find "$JDK_DOWNLOAD_DIR/extracted" -mindepth 1 -maxdepth 1 -type d -print -quit)"
    if [[ -z "$EXTRACTED_JDK" || ! -x "$EXTRACTED_JDK/bin/java" ]]; then
      echo "ERROR: The downloaded archive did not contain a usable JDK." >&2
      exit 1
    fi
    if [[ "$(java_major_version "$EXTRACTED_JDK/bin/java")" != "17" ]]; then
      echo "ERROR: The downloaded Java runtime is not JDK 17." >&2
      exit 1
    fi

    STAGING_JDK="$CACHE_DIR/.jdk-17-install-$$"
    rm -rf "$STAGING_JDK"
    mv "$EXTRACTED_JDK" "$STAGING_JDK"
    if [[ -e "$CACHED_JDK" ]]; then
      rm -rf "$CACHED_JDK"
    fi
    mv "$STAGING_JDK" "$CACHED_JDK"
    JAVA_HOME="$CACHED_JDK"
  fi
fi

SDK_ROOT_CANDIDATE="${FOCUSFLOW_ANDROID_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}}"
LOCAL_SDK_ROOT=""
if [[ -f local.properties ]]; then
  LOCAL_SDK_ROOT="$(sed -nE 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' local.properties | tail -n 1)"
  if [[ "$LOCAL_SDK_ROOT" == *\\* ]]; then
    LOCAL_SDK_ROOT="$(printf '%s\n' "$LOCAL_SDK_ROOT" | sed -e 's/\\ / /g' -e 's/\\:/:/g' -e 's/\\\\/\\/g')"
  fi
fi
if [[ -n "$LOCAL_SDK_ROOT" ]]; then
  if [[ -n "$SDK_ROOT_CANDIDATE" ]] &&
    [[ "$(canonical_path "$SDK_ROOT_CANDIDATE")" != "$(canonical_path "$LOCAL_SDK_ROOT")" ]]; then
    echo "ERROR: local.properties sdk.dir conflicts with ANDROID_SDK_ROOT/ANDROID_HOME." >&2
    echo "Set both to the same external SDK directory, or remove the local sdk.dir entry." >&2
    exit 1
  fi
  SDK_ROOT_CANDIDATE="$LOCAL_SDK_ROOT"
fi
if [[ -z "$SDK_ROOT_CANDIDATE" ]]; then
  SDK_ROOT_CANDIDATE="${HOME:-/tmp}/.cache/focusflow/android-sdk"
fi

assert_sdk_outside_repo "$SDK_ROOT_CANDIDATE"
SDK_ROOT="$(canonical_path "$SDK_ROOT_CANDIDATE")"
mkdir -p "$SDK_ROOT"
SDK_ROOT="$(cd -P "$SDK_ROOT" && pwd -P)"
assert_sdk_outside_repo "$SDK_ROOT"
SDK_TEMP_BASE="$(canonical_path "${TMPDIR:-/tmp}")"
if [[ "$SDK_TEMP_BASE" == "$ROOT_DIR" || "$SDK_TEMP_BASE" == "$ROOT_DIR/"* ]]; then
  SDK_TEMP_BASE="$(canonical_path /tmp)"
fi
assert_path_outside_repo "$SDK_TEMP_BASE" "Android SDK download temporary directories"
mkdir -p "$SDK_TEMP_BASE"
export TMPDIR="$SDK_TEMP_BASE"

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
echo "JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version

echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT (outside the Git repository)"

# Pinned command-line tools archive and digest from Google's official SDK repository.
ANDROID_CMDLINE_TOOLS_REVISION="16111833"
ANDROID_CMDLINE_TOOLS_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
ANDROID_CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REVISION}_latest.zip"
ANDROID_CLI="$SDK_ROOT/cmdline-tools/latest/bin/android"

if [[ ! -x "$ANDROID_CLI" ]]; then
  SDK_DOWNLOAD_DIR="$(mktemp -d "${SDK_TEMP_BASE%/}/focusflow-android-sdk.XXXXXX")"
  echo "Downloading Android SDK command-line tools from Google..."
  curl --fail --location --retry 3 --connect-timeout 20 \
    "$ANDROID_CMDLINE_TOOLS_URL" \
    --output "$SDK_DOWNLOAD_DIR/commandline-tools.zip"
  printf '%s  %s\n' "$ANDROID_CMDLINE_TOOLS_SHA1" "$SDK_DOWNLOAD_DIR/commandline-tools.zip" \
    | sha1sum --check
  unzip -tq "$SDK_DOWNLOAD_DIR/commandline-tools.zip"
  mkdir -p "$SDK_DOWNLOAD_DIR/extracted" "$SDK_ROOT/cmdline-tools"
  unzip -q "$SDK_DOWNLOAD_DIR/commandline-tools.zip" -d "$SDK_DOWNLOAD_DIR/extracted"
  if [[ ! -x "$SDK_DOWNLOAD_DIR/extracted/cmdline-tools/bin/android" ]]; then
    echo "ERROR: The Android command-line tools archive has an unexpected layout." >&2
    exit 1
  fi

  TOOLS_STAGING="$SDK_ROOT/cmdline-tools/.latest-install-$$"
  TOOLS_BACKUP="$SDK_ROOT/cmdline-tools/.latest-backup-$$"
  rm -rf -- "$TOOLS_STAGING" "$TOOLS_BACKUP"
  mv "$SDK_DOWNLOAD_DIR/extracted/cmdline-tools" "$TOOLS_STAGING"
  if [[ -e "$SDK_ROOT/cmdline-tools/latest" ]]; then
    mv "$SDK_ROOT/cmdline-tools/latest" "$TOOLS_BACKUP"
  fi
  if ! mv "$TOOLS_STAGING" "$SDK_ROOT/cmdline-tools/latest"; then
    if [[ -e "$TOOLS_BACKUP" && ! -e "$SDK_ROOT/cmdline-tools/latest" ]]; then
      mv "$TOOLS_BACKUP" "$SDK_ROOT/cmdline-tools/latest"
    fi
    echo "ERROR: Could not install Android SDK command-line tools." >&2
    exit 1
  fi
  rm -rf -- "$TOOLS_BACKUP"
fi

if [[ ! -x "$ANDROID_CLI" ]]; then
  echo "ERROR: Android CLI was not installed at $ANDROID_CLI." >&2
  exit 1
fi

echo "Installing Android SDK packages with metrics disabled; accepting required SDK licenses..."
set +o pipefail
yes | "$ANDROID_CLI" --no-metrics --sdk="$SDK_ROOT" sdk install \
  platforms/android-35 \
  build-tools/35.0.0 \
  platform-tools
set -o pipefail

./gradlew "${GRADLE_TASKS[@]}" --no-daemon --console=plain --stacktrace

if [[ "$SKIP_APK_BUILD" -eq 0 ]]; then
  APK_PATH="app/build/outputs/apk/production/debug/app-production-debug.apk"
  if [[ ! -f "$APK_PATH" ]]; then
    echo "ERROR: Expected APK was not produced at $APK_PATH" >&2
    exit 1
  fi

  echo "APK: $APK_PATH"
  sha256sum "$APK_PATH"
else
  echo "Gradle tasks passed: ${GRADLE_TASKS[*]}"
fi

echo "Finished: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"