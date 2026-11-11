# Security hardening — progress

Source plan: `SECURITY_HARDENING_PLAN.md`. Status of each item in this snapshot.
**Nothing here has been compiled or run** (no Android SDK in the authoring environment) — run
`./gradlew testDebugUnitTest assembleDebug` and the device checks below before merging.

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

### Open question found during P2 (not changed)
`LauncherActivity` reads the pref key `launcher_wallpaper`, but `LauncherSetupScreen` / `SettingsRepository`
write `launcher_wallpaper_uri`, and nothing writes `launcher_wallpaper`. So a wallpaper picked in Launcher setup
is stored but the launcher itself does not appear to display it. This predates the hardening work; I left the
behaviour exactly as it was. Decide whether the launcher should read `launcher_wallpaper_uri`.

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
- **P5.1 NOT DONE — needs a decision.** The plan assumes OkHttp, but this project has no OkHttp: `UrlConnectionFetch`
  uses `HttpsURLConnection`, which cannot be told to connect to an already-validated address. Options:
  (a) add `com.squareup.okhttp3:okhttp` and implement the plan's custom `Dns` (new dependency, ~0.8 MB, R8 rules);
  (b) hand-write a pinned-socket HTTPS client (connect to the validated IP, TLS with the real host name for SNI and
  certificate checks) — no new dependency but a bespoke HTTP/1.1 parser (chunked bodies etc.), which I would not ship
  untested; (c) leave as is: the code already documents it as a known limit and TLS certificate validation makes it
  hard to exploit. Severity is Low either way.

## P3 — not started
