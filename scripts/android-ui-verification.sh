#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

SDK_ROOT="${FOCUSFLOW_ANDROID_SDK_ROOT:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${HOME}/.cache/focusflow/android-sdk}}}"
SDK_ROOT="$(readlink -m "$SDK_ROOT")"
case "$SDK_ROOT" in
  "$ROOT_DIR"|"$ROOT_DIR"/*)
    echo "ERROR: Android SDK must stay outside the repository: $SDK_ROOT" >&2
    exit 1
    ;;
esac

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
export ANDROID_AVD_HOME="${FOCUSFLOW_ANDROID_AVD_HOME:-${HOME}/.android/avd}"
export DISPLAY="${DISPLAY:-:0}"

AVD_NAME="${FOCUSFLOW_AVD_NAME:-FocusFlowApi35}"
AVD_DEVICE="${FOCUSFLOW_AVD_DEVICE:-pixel_2}"
SYSTEM_IMAGE="system-images;android-35;google_apis;x86_64"
APK_PATH="app/build/outputs/apk/production/debug/app-production-debug.apk"
APPLICATION_ID="com.tbtechs.focusflow"

echo "== FocusFlow Android UI verification environment =="
echo "Android SDK: $ANDROID_SDK_ROOT"
echo "Android AVD home: $ANDROID_AVD_HOME"
echo "Replit desktop display: $DISPLAY"

write_probe=""
if ! write_probe="$(mktemp "$ANDROID_SDK_ROOT/.focusflow-write-probe.XXXXXX" 2>/dev/null)"; then
  echo "ERROR: Replit's storage quota is currently exhausted." >&2
  echo "Free or increase several GiB of available storage before installing the emulator image." >&2
  exit 1
fi
rm -f "$write_probe"

echo "== Building the production debug APK and bootstrapping base Android tools =="
bash "$ROOT_DIR/scripts/build-apk-with-java.sh"

# The build bootstrap may have installed its JDK inside the cache in a child shell.
CACHED_JDK="${HOME}/.cache/focusflow/android-build/jdk-17"
if [[ -x "$CACHED_JDK/bin/java" ]]; then
  export JAVA_HOME="$CACHED_JDK"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

ANDROID_CLI="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/android"
AVDMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

for tool in "$ANDROID_CLI" "$AVDMANAGER" "$ADB"; do
  if [[ ! -x "$tool" ]]; then
    echo "ERROR: Required Android SDK tool is missing: $tool" >&2
    exit 1
  fi
done
if [[ ! -f "$APK_PATH" ]]; then
  echo "ERROR: Expected production debug APK was not produced: $APK_PATH" >&2
  exit 1
fi

echo "== Ensuring emulator and API 35 system image are installed =="
set +o pipefail
yes | "$ANDROID_CLI" --no-metrics --sdk="$ANDROID_SDK_ROOT" sdk install \
  emulator "system-images/android-35/google_apis/x86_64"
set -o pipefail

if [[ ! -x "$EMULATOR" ]]; then
  echo "ERROR: Android emulator binary was not installed at $EMULATOR" >&2
  exit 1
fi

mkdir -p "$ANDROID_AVD_HOME"
AVD_CONFIG="$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini"
if [[ ! -f "$AVD_CONFIG" ]]; then
  echo "== Creating AVD $AVD_NAME =="
  printf 'no\n' | "$AVDMANAGER" create avd \
    --name "$AVD_NAME" \
    --package "$SYSTEM_IMAGE" \
    --device "$AVD_DEVICE"
fi

if ! command -v xwininfo >/dev/null 2>&1; then
  echo "ERROR: xwininfo is needed to verify Replit's native-window display." >&2
  exit 1
fi
if ! xwininfo -display "$DISPLAY" -root >/dev/null 2>&1; then
  echo "ERROR: Replit's X11 display is not reachable at DISPLAY=$DISPLAY." >&2
  exit 1
fi

SERIAL=""
while read -r candidate state; do
  [[ "$candidate" == emulator-* && "$state" == device ]] || continue
  candidate_avd="$("$ADB" -s "$candidate" emu avd name 2>/dev/null | sed -n '1p' | tr -d '\r')"
  if [[ "$candidate_avd" == "$AVD_NAME" ]]; then
    SERIAL="$candidate"
    echo "Reusing running AVD $AVD_NAME on $SERIAL"
    break
  fi
done < <("$ADB" devices | awk 'NR > 1 { print $1, $2 }')

EMULATOR_PID=""
EMULATOR_LOG="${HOME}/.cache/focusflow/android-ui-verification/emulator.log"
mkdir -p "$(dirname "$EMULATOR_LOG")"

cleanup() {
  if [[ -n "$EMULATOR_PID" ]] && kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "Stopping emulator started by this workflow..."
    "$ADB" -s "$SERIAL" emu kill >/dev/null 2>&1 || kill "$EMULATOR_PID" 2>/dev/null || true
    wait "$EMULATOR_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if [[ -z "$SERIAL" ]]; then
  port=5554
  while "$ADB" devices | grep -q "^emulator-${port}[[:space:]]"; do
    port=$((port + 2))
  done
  SERIAL="emulator-$port"

  emulator_args=(
    -avd "$AVD_NAME"
    -port "$port"
    -no-audio
    -no-boot-anim
    -no-snapshot
    -gpu swiftshader_indirect
    -cores 2
    -memory 2048
  )
  if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
    echo "No usable /dev/kvm; starting in CPU-only mode."
    emulator_args+=(-accel off)
  fi

  echo "== Starting the visible emulator window =="
  echo "Replit should expose it in the VNC pane when the native window opens."
  "$EMULATOR" "${emulator_args[@]}" >"$EMULATOR_LOG" 2>&1 &
  EMULATOR_PID=$!
fi

boot_timeout="${FOCUSFLOW_EMULATOR_BOOT_TIMEOUT_SECONDS:-900}"
deadline=$((SECONDS + boot_timeout))
echo "== Waiting for Android to finish booting (up to ${boot_timeout}s) =="
while (( SECONDS < deadline )); do
  state="$("$ADB" -s "$SERIAL" get-state 2>/dev/null || true)"
  if [[ "$state" == "device" ]]; then
    booted="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$booted" == "1" ]]; then
      break
    fi
  fi
  if [[ -n "$EMULATOR_PID" ]] && ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
    echo "ERROR: Emulator exited before Android finished booting." >&2
    tail -n 100 "$EMULATOR_LOG" >&2 || true
    exit 1
  fi
  sleep 5
done

if [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" != "1" ]]; then
  echo "ERROR: Android did not finish booting within ${boot_timeout}s." >&2
  tail -n 100 "$EMULATOR_LOG" >&2 || true
  exit 1
fi

echo "== Installing and launching FocusFlow =="
"$ADB" -s "$SERIAL" install -r "$APK_PATH"
"$ADB" -s "$SERIAL" shell monkey -p "$APPLICATION_ID" \
  -c android.intent.category.LAUNCHER 1
echo "FocusFlow is ready on $SERIAL. Use the Replit VNC pane for visual interaction."
echo "Emulator output log: $EMULATOR_LOG"
echo "Streaming Android runtime errors below; stop this workflow to end the session."
"$ADB" -s "$SERIAL" logcat -v time -s AndroidRuntime:E System.err:E
