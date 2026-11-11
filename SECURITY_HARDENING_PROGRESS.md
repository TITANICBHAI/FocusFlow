# Security hardening — progress

Source plan: `SECURITY_HARDENING_PLAN.md`. Status of each item in this snapshot.
First CI run (tbtechsdev debug): all main and test code compiled and 1,173 of 1,174 unit tests passed; the one failure was a wrong
expected value in my own `WallpaperPathPolicyTest.sampleSizeKeepsBothDimensionsAtOrAboveTheScreen` (the code was right, the
test said 4 where 2 is correct for 12000x9000). Fixed in the test only. The APK assemble step and the device checks below
have not run yet.

## P1 — Not-exported dynamic receivers: DONE (needs device check)
`TaskAlarmActivity`, `FocusSessionViewModel`, `AppBlockerAccessibilityService` now use
`ContextCompat.registerReceiver(..., ContextCompat.RECEIVER_NOT_EXPORTED)` (androidx.core 1.15.0 ≥ 1.9.0).
All senders of the three custom actions are in-app and already use `setPackage` / `sendBroadcast` from the
same UID, so they still match. System actions (`SCREEN_OFF`, `USER_PRESENT`) are still delivered.
Device checks: Android 14+ service connects and an allowance-config change re-evaluates; on API 29-32
`adb shell am broadcast -a <custom action>` no longer reaches the receivers.

## P2 — Wallpaper path: DONE except one open question (below)
- New `data/images/WallpaperPathPolicy` (canonical path must be inside `filesDir`; `content://` and other
  schemes rejected), `WallpaperLoader` (bounds check, `inSampleSize` to screen, background thread, null on any
  failure), `WallpaperStore` (validate + re-encode to WebP into `filesDir/launcher_wallpaper/`, one file kept).
- The three raw `BitmapFactory` decodes (`BlockOverlayActivity`, `LauncherActivity`,
  `AppBlockerAccessibilityService`) now call `WallpaperLoader.loadInto`.
- `launcherWallpaperUri` added to device-local fields in `PortableSettingsPolicy`: never exported, never imported
  (its mapping in `BackupSettingsAdapter` removed; the one test expectation that asserted it was updated).
- `LauncherSetupScreen` ingests the picked image through `WallpaperStore`; "Clear wallpaper" also deletes the file.
- `SettingsRepository.setLauncherWallpaperUri` and `BlockOverlayController.setOverlayWallpaper` refuse paths
  outside `filesDir`.
- Tests added: `WallpaperPathPolicyTest`, `WallpaperStoreTest`, `WallpaperBackupPolicyTest`.

### Launcher wallpaper wiring: FIXED
`LauncherActivity` read the pref key `launcher_wallpaper` while Launcher setup and `SettingsRepository` write
`launcher_wallpaper_uri`, and nothing ever wrote `launcher_wallpaper` — so a picked wallpaper was saved but never shown
(the neighbouring `launcher_theme` key matched on both sides, which is how this was confirmed as a typo-style bug).
Now one shared constant, `SettingsRepository.PREF_LAUNCHER_WALLPAPER_URI`, is used by the writer, the reader and the
launcher, so they cannot drift again. `readAppSettings` also treats a value that is not a readable file in app-private
storage (for example a stale `content://` URI saved by an older build) as "no wallpaper". The wallpaper shows in the
Glassy launcher style only (that is where the existing code draws it).
Manual test steps: `SECURITY_TESTING_GUIDE.md` (test files are in `FocusFlow-test-files.zip`, shared separately).

## P4 — Small items: DONE (needs build check)
1. `PackageInstallReceiver` is now `exported="false"` (system PACKAGE_* broadcasts are still delivered).
2. `FocusFlowWidget` ignores `ACTION_REFRESH` broadcasts arriving less than 2 s after the previous one. The app's own
   alarm tick is scheduled at least 15 s ahead, so it is unaffected. The receiver stays exported (widget hosts need it);
   `APPWIDGET_UPDATE`, `TIME_SET` and `TIMEZONE_CHANGED` are not throttled. Test: `WidgetRefreshThrottleTest`.
3. Added `res/xml/network_security_config.xml` (cleartext off, system CAs) and referenced it from `<application>`.
   Checked first: no `http://` URLs or localhost use in app code, so nothing depends on cleartext.
4. Removed `BIND_ACCESSIBILITY_SERVICE` from `<uses-permission>` (kept on the `<service>`; no code references it).
   - NOT changed: `QUERY_ALL_PACKAGES` is still declared. The launcher/app-picker/blocker rely on seeing all apps, so
     removing it would break features; whether Play accepts it is a store-declaration question, not a code change.
   - NOT changed: `BIND_VPN_SERVICE` is also listed as `<uses-permission>` (same pattern as the accessibility one, also
     harmless). Say the word and I will remove it too.

## P5 — Downloader polish
- **P5.2 DONE (needs build check):** the "Download pictures?" dialog now lists *every* distinct host (lower-cased,
  in a scrollable list, never truncated). It previously showed at most three names plus "and N more websites".
- **P5.1 DECIDED: leave as is (option c).** The plan assumes OkHttp, but this project has no OkHttp: `UrlConnectionFetch`
  uses `HttpsURLConnection`, which cannot be told to connect to an already-validated address. Options:
  (a) add `com.squareup.okhttp3:okhttp` and implement the plan's custom `Dns` (new dependency, ~0.8 MB, R8 rules);
  (b) hand-write a pinned-socket HTTPS client (connect to the validated IP, TLS with the real host name for SNI and
  certificate checks) — no new dependency but a bespoke HTTP/1.1 parser (chunked bodies etc.), which I would not ship
  untested; (c) leave as is: the code already documents it as a known limit and TLS certificate validation makes it
  hard to exploit. Severity is Low either way.

## P3 — Isolated image decoding: DONE (needs on-device verification)
- New `ImageDecodeService` (`android:isolatedProcess="true"`, not exported) and `IsolatedImageCodec`, an `ImageCodec`
  that sends bytes through pipes and gets bounds / orientation / re-encoded WebP back. A `Messenger` is used instead of
  AIDL, so no Gradle change is needed. Protocol constants live in `ImageDecodeProtocol`.
- `AppModule` now gives `SubtaskImageStore` and `WallpaperStore` the isolated codec. All existing magic-byte, size and
  dimension checks stay in the main process. One private constant, `ISOLATE_IMAGE_DECODING`, switches back to the
  in-process codec if you ever need to rule isolation out while debugging.
- Fails closed: a process that cannot start, crashes or takes more than 10 s makes the call return null, so the image is
  rejected; the next call starts a fresh process. The isolated process is released after 20 s idle.
- `FocusFlowApp.onCreate` now returns immediately when `Process.isIsolated()`. Without this the isolated process would
  run the full app startup (database, workers) and die. This is the line to check first if decoding ever fails.
- Not covered by this item: `WallpaperLoader` still decodes stored wallpaper files in-process. Files from the picker are
  re-encoded by the isolated codec first; overlay wallpapers copied by `OverlayAppearanceModal` are not.
- New `androidTest` `IsolatedImageCodecTest` (bounds, re-encode, garbage input then recovery, back-to-back calls).
- Device checks: pick / download / restore pictures and a launcher wallpaper; `adb shell ps | grep focusflow` shows a
  `:ImageDecodeService`-style isolated process while importing and it disappears after ~20 s; a corrupt image is rejected
  and the next import works.
