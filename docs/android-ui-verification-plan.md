# Android UI verification plan for Replit

## Status

Plan only. No emulator package or Android system image has been downloaded, and no APK has been installed or launched for this check.

The JVM unit-test workflow now passes for both `productionDebug` and `tbtechsdevDebug`. The remaining gap is exercising the add, selection/tick, and defense-PIN prompt interactions in the running Android app. The current Replit environment has no connected device, no installed emulator, and no `/dev/kvm`; the emulator would therefore have to run without hardware acceleration.

## Proposed approach

### 1. Keep emulator files outside the repository

Reuse the SDK cache created by the Android build bootstrap:

```text
SDK: $HOME/.cache/focusflow/android-sdk
AVD data: $HOME/.android/avd
```

After approval to proceed, install only the emulator and an API 35 Google APIs x86_64 system image with the SDK manager under that SDK root:

```bash
SDK="$HOME/.cache/focusflow/android-sdk"
"$SDK/cmdline-tools/latest/bin/sdkmanager" \
  --sdk_root="$SDK" \
  "emulator" \
  "system-images;android-35;google_apis;x86_64"
```

Then list available device profiles and create an AVD from one of them. Keep its name and files in the user-level AVD directory, not in the project.

### 2. Boot in software mode

Check the installed emulator's help for supported acceleration and graphics flags, then launch it headlessly with acceleration disabled and a software renderer. The intended command shape is:

```bash
"$SDK/emulator/emulator" \
  -avd FocusFlowApi35 \
  -no-window -no-audio -no-boot-anim -no-snapshot \
  -accel off -gpu swiftshader_indirect
```

Wait for `adb wait-for-device` and for `sys.boot_completed` to report `1`. Because `/dev/kvm` is absent, boot and interaction may be slow. If the image cannot boot reliably or the CPU-only run is impractical, stop there and use a connected Android device instead of repeatedly downloading or retrying.

### 3. Build and install a debug app

Build the production debug APK with the repository's existing bootstrap script. This uses the cached JDK and SDK, and keeps generated build output under the ignored `app/build` directory:

```bash
bash scripts/build-apk-with-java.sh
find app/build/outputs/apk -type f -name '*.apk' -print
```

Install the resulting `productionDebug` APK and launch FocusFlow on the emulator. Any permissions needed for a flow should be granted only in this disposable test environment.

### 4. Exercise and record the flows

Use a clearly synthetic manual package name, such as `com.example.focusflowcheck`; it does not need to be installed for the manual-package list checks.

1. **Advanced manual add:** open the Reference picker and add the package under Advanced; confirm it appears in the list and can be selected.
2. **Always On add and tick:** add the package from Always On's Advanced section; confirm it is selected, toggle it off and back on, save, reopen the screen, and confirm the saved selection remains.
3. **Daily Allowance add and save:** add the package under Advanced, tap its row to create the default Count entry, save, reopen, and confirm the configured entry remains.
4. **PIN prompts:** set a test defense PIN on the emulator, then attempt a protected removal in Always On and Daily Allowance. Confirm each prompt appears, cancellation preserves the item, an incorrect PIN does not remove it, and the correct PIN permits the intended removal. Also check the Standalone Block clear prompt if that is part of the requested PIN flow.
5. **Evidence:** capture screenshots at each relevant state and inspect `adb logcat` for crashes or coroutine errors. Keep screenshots and logs under `/tmp/focusflow-ui-verification`, not in Git.

The existing `androidTest` suite has instrumentation coverage for other app areas, but not these specific package-add, selection, or PIN-prompt flows. This pass is therefore a manual emulator/device check; it does not claim Compose UI-test coverage.

## Completion criteria

- The emulator boots and FocusFlow launches without a crash.
- The manual add, selection/tick, save-and-reopen, and PIN-prompt behaviors above match expectations.
- No relevant crash or uncaught coroutine exception appears in `logcat`.
- Emulator state and generated evidence remain outside the repository.

No `.replit` workflow or project dependency change is needed for this plan.
