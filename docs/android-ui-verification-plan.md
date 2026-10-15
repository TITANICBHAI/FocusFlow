# Android UI verification plan for Replit

## Status

The production debug APK built successfully. The emulator and API 35 image archives were downloaded into the external SDK cache, but installation was interrupted before either package was registered; no AVD has been created and no APK has been installed or launched.

The JVM unit-test workflow now passes for both `productionDebug` and `tbtechsdevDebug`. The remaining gap is exercising the add, selection/tick, and defense-PIN prompt interactions in the running Android app. The current Replit environment has no connected device, no installed emulator or system image, and no `/dev/kvm`; the emulator would therefore have to run without hardware acceleration.

During setup, writes started failing with `Disk quota exceeded` in both `$HOME/.cache` and `/tmp`, even though the filesystem reports free capacity. The cached package archives are retained for a possible resume. Do not delete existing build or SDK caches without the user's approval; the emulator image needs several additional GiB when unpacked.

## Replit VNC check

Replit's [VNC documentation](https://docs.replit.com/additional-resources/streaming-native-graphics-vnc) says native application windows are streamed to the browser automatically; a separate `vncserver` launch is not required. In this workspace, `DISPLAY=:0` is set, `/tmp/.X11-unix/X0` exists, and `xwininfo` successfully queried the root window. No emulator window has been opened yet. The `vncserver` command is present, but its wrapper reports that `xinit` is missing, so the plan does not depend on launching it directly.

The Replit documentation describes native-window streaming generally and does not specifically confirm Android Emulator compatibility. Treat showing the emulator window in the VNC pane as the first smoke check after setup; do not install extra desktop packages just to force a separate VNC server.

## Proposed approach

### 1. Keep emulator files outside the repository

Reuse the SDK cache created by the Android build bootstrap:

```text
SDK: $HOME/.cache/focusflow/android-sdk
AVD data: $HOME/.android/avd
```

The repeatable setup script uses the Android CLI to install only the emulator and an API 35 Google APIs x86_64 system image under that SDK root:

```bash
bash scripts/android-ui-verification.sh
```

Then list available device profiles and create an AVD from one of them. Keep its name and files in the user-level AVD directory, not in the project.

### 2. Launch the emulator in Replit's VNC display

Check the installed emulator's help for supported acceleration and graphics flags. Inherit Replit's existing `DISPLAY=:0` and launch with its GUI enabled so the native emulator window can appear in the VNC pane. Do not pass `-no-window`. The intended command shape is:

```bash
export DISPLAY=:0
"$SDK/emulator/emulator" \
  -avd FocusFlowApi35 \
  -no-audio -no-boot-anim -no-snapshot \
  -accel off -gpu swiftshader_indirect
```

Confirm that the emulator window appears in Replit's VNC pane, then wait for `adb wait-for-device` and for `sys.boot_completed` to report `1`. Because `/dev/kvm` is absent, boot and interaction may be slow. If the window does not appear, the image cannot boot reliably, or the CPU-only run is impractical, stop there and use a connected Android device instead of repeatedly downloading or retrying.

### 3. Build and install a debug app

Build the production debug APK with the repository's existing bootstrap script. This uses the cached JDK and SDK, and keeps generated build output under the ignored `app/build` directory:

```bash
bash scripts/build-apk-with-java.sh
find app/build/outputs/apk -type f -name '*.apk' -print
```

Install the resulting `productionDebug` APK and launch FocusFlow on the emulator. Use Replit's VNC pane for the visible UI; use `adb shell input` where repeatable taps or text entry are needed. Any permissions needed for a flow should be granted only in this disposable test environment.

### 4. Exercise and record the flows

Use a clearly synthetic manual package name, such as `com.example.focusflowcheck`; it does not need to be installed for the manual-package list checks.

1. **Advanced manual add:** open the Reference picker and add the package under Advanced; confirm it appears in the list and can be selected.
2. **Always On add and tick:** add the package from Always On's Advanced section; confirm it is selected, toggle it off and back on, save, reopen the screen, and confirm the saved selection remains.
3. **Daily Allowance add and save:** add the package under Advanced, tap its row to create the default Count entry, save, reopen, and confirm the configured entry remains.
4. **PIN prompts:** set a test defense PIN on the emulator, then attempt a protected removal in Always On and Daily Allowance. Confirm each prompt appears, cancellation preserves the item, an incorrect PIN does not remove it, and the correct PIN permits the intended removal. Also check the Standalone Block clear prompt if that is part of the requested PIN flow.
5. **Evidence:** capture emulator screenshots with `adb exec-out screencap -p` at each relevant state and inspect `adb logcat` for crashes or coroutine errors. Keep screenshots and logs under `/tmp/focusflow-ui-verification`, not in Git.

The existing `androidTest` suite has instrumentation coverage for other app areas, but not these specific package-add, selection, or PIN-prompt flows. This pass is therefore a manual emulator/device check; it does not claim Compose UI-test coverage.

## Completion criteria

- The emulator boots and FocusFlow launches without a crash.
- The manual add, selection/tick, save-and-reopen, and PIN-prompt behaviors above match expectations.
- No relevant crash or uncaught coroutine exception appears in `logcat`.
- Emulator state and generated evidence remain outside the repository.

The requested console workflow is intended to run `bash scripts/android-ui-verification.sh` on demand. The script bootstraps the APK, ensures SDK packages and an AVD exist, starts the visible emulator, installs and launches FocusFlow, then streams Android runtime errors to the workflow console. The add/tick/PIN interactions remain manual in the VNC pane; the workflow does not synthesize those taps.

The script checks whether the SDK cache is writable before doing further work and exits with a clear quota message if not. The console workflow is not started while the emulator packages remain uninstalled.
