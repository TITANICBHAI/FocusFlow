# FocusFlow Migration — Stage 2 Consolidated Handoff

This is the single Stage 3 handoff for the work covered by Prompts 1–6. It
includes the ten Android bridge modules, the two supporting-logic ports, the two
picker patterns that replace bridge modules, and the notification/backup ports.

The Kotlin targets are under
`app/src/main/java/com/tbtechs/focusflow/` unless a more specific path is shown.
`Promise<T>` means the old React Native bridge contract. Kotlin repository and
controller methods are `suspend` unless explicitly marked as a pure synchronous
function.

## Consolidated migration table

| Source file | Target file(s) | Public methods (old → new signature) | Flags |
|---|---|---|---|
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/SharedPrefsModule.kt` | `data/repository/SettingsRepository.kt` | `setFocusActive(active: Boolean, pinHash: String?): Promise<void>` → `suspend fun setFocusActive(active: Boolean, pinHash: String? = null)`;<br>`setFocusBreak(active: Boolean, untilMs: number): Promise<void>` → `suspend fun setFocusBreak(active: Boolean, untilMs: Long)`;<br>`clearFocusBreak(): Promise<void>` → `suspend fun clearFocusBreak()`;<br>`getFocusBreakUntilMs(): Promise<number>` → `suspend fun getFocusBreakUntilMs(): Long`;<br>`setAllowedPackages(packages: ReadableArray): Promise<void>` → `suspend fun setAllowedPackages(packages: List<String>)`;<br>`setActiveTask(taskId: String, name: String, endMs: number, nextName: String?): Promise<void>` → `suspend fun setActiveTask(taskId: String, name: String, endMs: Long, nextName: String?)`;<br>`setActiveTaskColor(colorHex: String): Promise<void>` → `suspend fun setActiveTaskColor(colorHex: String)`;<br>`setActiveTaskStartMs(taskId: String, startMs: number): Promise<void>` → `suspend fun setActiveTaskStartMs(taskId: String, startMs: Long)`;<br>`clearActiveTask(): Promise<void>` → `suspend fun clearActiveTask()`;<br>`publishFocusSnapshot(active, taskId, taskName, taskEndMs, taskColor, allowedPackages, nextTaskName, pinHash): Promise<void>` → `suspend fun publishFocusSnapshot(active: Boolean, taskId: String?, taskName: String?, taskEndMs: Long, taskColor: String?, allowedPackages: List<String>?, nextTaskName: String?, pinHash: String?)`;<br>`publishFocusSnapshotInternal(active, taskId, taskName, taskEndMs, taskColor, allowedPackages, nextTaskName): Promise<void>` → `suspend fun publishFocusSnapshotInternal(active: Boolean, taskId: String?, taskName: String?, taskEndMs: Long, taskColor: String?, allowedPackages: List<String>?, nextTaskName: String?)`;<br>`pushWidgetUpdate(): Promise<void>` → `suspend fun pushWidgetUpdate()`;<br>`setStandaloneBlock(active, packages, untilMs, pinHash): Promise<void>` → `suspend fun setStandaloneBlock(active: Boolean, packages: List<String>, untilMs: Long, pinHash: String? = null)`;<br>`publishStandaloneSnapshot(active, packages, untilMs, pinHash, vpnPackages): Promise<void>` → `suspend fun publishStandaloneSnapshot(active: Boolean, packages: List<String>, untilMs: Long, pinHash: String?, vpnPackages: List<String>?)`;<br>`setAlwaysBlockActive(active, packages): Promise<void>` → `suspend fun setAlwaysBlockActive(active: Boolean, packages: List<String>)`;<br>`publishScheduleVpnSnapshot(packagesJson: String): Promise<void>` → `suspend fun publishScheduleVpnSnapshot(packagesJson: String)`;<br>`setDailyAllowancePackages(packages): Promise<void>` → `suspend fun setDailyAllowancePackages(packages: List<String>)`;<br>`setBlockedWords(words): Promise<void>` → `suspend fun setBlockedWords(words: List<String>)`;<br>`setLauncherDockPackages(packagesJson: String): Promise<void>` → `suspend fun setLauncherDockPackages(packagesJson: String)`;<br>`setSystemGuardEnabled(enabled: Boolean): Promise<void>` → `suspend fun setSystemGuardEnabled(enabled: Boolean)`;<br>`setBlockInstallActionsEnabled(enabled: Boolean): Promise<void>` → `suspend fun setBlockInstallActionsEnabled(enabled: Boolean)`;<br>`setBlockYoutubeShortsEnabled(enabled: Boolean): Promise<void>` → `suspend fun setBlockYoutubeShortsEnabled(enabled: Boolean)`;<br>`setBlockInstagramReelsEnabled(enabled: Boolean): Promise<void>` → `suspend fun setBlockInstagramReelsEnabled(enabled: Boolean)`;<br>`setNetworkBlockEnabled(enabled: Boolean): Promise<void>` → `suspend fun setNetworkBlockEnabled(enabled: Boolean)`;<br>`setVpnSelectedPackages(packagesJson: String): Promise<void>` → `suspend fun setVpnSelectedPackages(packagesJson: String)`;<br>`setDailyAllowanceConfig(configJson: String): Promise<void>` → `suspend fun setDailyAllowanceConfig(configJson: String)`;<br>`putString(key: String, value: String): Promise<void>` → `suspend fun putString(key: String, value: String)`;<br>`getString(key: String): Promise<String?>` → `suspend fun getString(key: String): String?`;<br>`getLong(key: String): Promise<number>` → `suspend fun getLong(key: String): Long`;<br>`getAllowanceSnapshot(): Promise<AllowanceSnapshot>` → `suspend fun getAllowanceSnapshot(): AllowanceSnapshot`;<br>`isDebuggable(): Promise<boolean>` → `suspend fun isDebuggable(): Boolean`;<br>`setDailyStats(tasksDone, tasksTotal, focusMins, streakDays): Promise<void>` → `suspend fun setDailyStats(tasksDone: Int, tasksTotal: Int, focusMins: Int, streakDays: Int)`;<br>`setLauncherHiddenPackages(packagesJson: String): Promise<void>` → `suspend fun setLauncherHiddenPackages(packagesJson: String)`;<br>`setLauncherTheme(theme: String): Promise<void>` → `suspend fun setLauncherTheme(theme: String)`;<br>`setFocusToolPackages(packagesJson: String): Promise<void>` → `suspend fun setFocusToolPackages(packagesJson: String)`;<br>`setLauncherLockDuringStandalone(enabled: Boolean): Promise<void>` → `suspend fun setLauncherLockDuringStandalone(enabled: Boolean)`;<br>`setLauncherBlockUninstall(enabled: Boolean): Promise<void>` → `suspend fun setLauncherBlockUninstall(enabled: Boolean)`;<br>`isDefaultLauncher(): Promise<boolean>` → `suspend fun isDefaultLauncher(): Boolean`;<br>`setLauncherClockStyle(style: String): Promise<void>` → `suspend fun setLauncherClockStyle(style: String)`;<br>`resetDailyAllowanceUsage(packageName: String?): Promise<void>` → `suspend fun resetDailyAllowanceUsage(packageName: String?)`. Numeric JavaScript timestamps also have `Double` compatibility overloads for `setFocusBreak`, `setActiveTask`, `setActiveTaskStartMs`, `publishFocusSnapshot`, `publishFocusSnapshotInternal`, `setStandaloneBlock`, and `publishStandaloneSnapshot`. | Preserves the `focusday_prefs` namespace, all legacy key names, VPN/widget side effects, and the distinction between asynchronous `apply()` writes and synchronous snapshot `commit()` writes. `publishFocusSnapshotInternal` is deliberately separate and PIN-free for authorized system transitions; it must not be merged with the user-facing PIN-gated method. The key audit found the rich `daily_allowance_config` key alongside the legacy `daily_allowance_packages` key; the latter is retained for the existing bridge method and must not be silently renamed. Enforcement also owns additional runtime/recovery keys such as `active_session_open_at_ms`, `active_session_last_checkpoint_ms`, `daily_allowance_usage_stats_sync`, `net_block_self_heal`, and `net_block_global`; they are not invented or removed by this repository. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/ForegroundServiceModule.kt` | `data/repository/ForegroundServiceController.kt` | `startIdleService(): Promise<void>` → `suspend fun startIdleService()`;<br>`startService(taskId: String, taskName: String, startTimeMs: number, endTimeMs: number, nextName: String?): Promise<void>` → `suspend fun startService(taskId: String, taskName: String, startTimeMs: Long, endTimeMs: Long, nextName: String?)`;<br>`stopService(pinHash: String?): Promise<void>` → `suspend fun stopService(pinHash: String?)`;<br>`stopServiceInternal(): Promise<void>` → `suspend fun stopServiceInternal()`;<br>`updateNotification(taskId: String, taskName: String, endTimeMs: number, nextName: String?): Promise<void>` → `suspend fun updateNotification(taskId: String, taskName: String, endTimeMs: Long, nextName: String?)`;<br>`setBreak(untilMs: number): Promise<void>` → `suspend fun setBreak(untilMs: Long)`;<br>`clearBreak(): Promise<void>` → `suspend fun clearBreak()`;<br>`requestBatteryOptimizationExemption(): Promise<void>` → `suspend fun requestBatteryOptimizationExemption()`. `Double` compatibility overloads remain for the JavaScript timestamp methods. | Thin intent wrapper around the real `ForegroundTaskService`; its `ACTION_SET_IDLE`, `ACTION_SET_BREAK`, `ACTION_CLEAR_BREAK`, and `EXTRA_*` values come from that service rather than guessed strings. `stopServiceInternal` is deliberately PIN-free for authorized completion/skip/orphan cleanup. Battery-optimization navigation remains best-effort and resolves after the same direct → list → general Settings fallback sequence, including when an OEM rejects the intents. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/ForegroundLaunchModule.kt` | `data/repository/LauncherController.kt` | `goHome(): Promise<void>` → `suspend fun goHome()`;<br>`bringToFront(): Promise<void>` → `suspend fun bringToFront()`;<br>`showOverlay(message: String): Promise<void>` → `suspend fun showOverlay(message: String)`;<br>`hasOverlayPermission(): Promise<boolean>` → `suspend fun hasOverlayPermission(): Boolean`;<br>`requestOverlayPermission(): Promise<void>` → `suspend fun requestOverlayPermission()`. | Controls the real launcher activity through home/package intents and does not duplicate launcher UI state. `showOverlay` remains the existing placeholder that brings FocusFlow to the front; the full-screen lock activity, manifest entry, full-screen intent/overlay permission decision, back handling, and dismissal lifecycle remain deferred. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/BlockOverlayModule.kt` | `data/repository/BlockOverlayController.kt` | `setOverlayQuote(quote: String): Promise<void>` → `suspend fun setOverlayQuote(quote: String)`;<br>`setCustomQuotes(quotesJson: String): Promise<void>` → `suspend fun setCustomQuotes(quotesJson: String)`;<br>`clearCustomQuote(): Promise<void>` → `suspend fun clearCustomQuote()`;<br>`setOverlayWallpaper(absolutePath: String): Promise<void>` → `suspend fun setOverlayWallpaper(absolutePath: String)`;<br>`clearOverlayWallpaper(): Promise<void>` → `suspend fun clearOverlayWallpaper()`;<br>`getDefaultQuotes(): Promise<String>` → `suspend fun getDefaultQuotes(): String`;<br>`getOverlaySettings(): Promise<String>` → `suspend fun getOverlaySettings(): String`. | Configuration-only controller; `BlockOverlayActivity` remains the enforcement UI owner. JSON validation, empty/`[]` reset behavior, wallpaper existence/readability checks, default quotes, and exact settings JSON keys are preserved. Any caller that launches an overlay must retain the source permission guard (`Settings.canDrawOverlays()`); this repository does not bypass or replace that enforcement-path check. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/AversionsModule.kt` | `data/repository/AversionsController.kt` | `getSettings(): Promise<{dimmerEnabled: boolean, vibrateEnabled: boolean, soundEnabled: boolean, weeklyReportEnabled: boolean}>` → `suspend fun getSettings(): AversionsSettings`;<br>`setSettings(settings: ReadableMap): Promise<void>` → `suspend fun setSettings(settings: AversionsSettingsUpdate)`. | All four preference flags and partial-update semantics are preserved. Updating `weeklyReportEnabled` still schedules or cancels the native weekly report through `TemptationLogManager`; the controller does not duplicate `AversiveActionsManager` behavior. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/NuclearModeModule.kt` | `data/repository/NuclearModeRepository.kt` | `requestUninstallApp(packageName: String): Promise<void>` → `suspend fun requestUninstallApp(packageName: String)`;<br>`requestUninstallApps(packagesJson: String): Promise<void>` → `suspend fun requestUninstallApps(packagesJson: String)`;<br>`isAppInstalled(packageName: String): Promise<boolean>` → `suspend fun isAppInstalled(packageName: String): Boolean`. | The bridge's main-thread `Handler` stagger is replaced by coroutine `delay(500)`, with no new thread/`Handler`/`AsyncTask`. Per-dialog launch failures remain swallowed as in the source; the system still requires the user to confirm every uninstall. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/InstalledAppsModule.kt` | `data/repository/InstalledAppsRepository.kt` | `getInstalledApps(): Promise<Array<{packageName: String, appName: String, isIme: boolean, iconBase64?: String}>>` → `suspend fun getInstalledApps(): List<InstalledAppInfo>`, where `InstalledAppInfo.icon` is a `Drawable?`. | Deliberate JS-bridge simplification: icons are returned as Android `Drawable` objects instead of PNG/base64. Launcher-visible apps and IMEs remain included, FocusFlow itself remains excluded, and Android package-visibility limits still apply. The future native UI must render `Drawable` directly rather than expecting `iconBase64`. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/GreyoutModule.kt` | `data/repository/GreyoutRepository.kt` | `getSchedule(): Promise<String>` → `suspend fun getSchedule(): String`;<br>`setSchedule(json: String): Promise<void>` → `suspend fun setSchedule(json: String)`;<br>`getTemptationLog(): Promise<String>` → `suspend fun getTemptationLog(): String`;<br>`clearTemptationLog(): Promise<void>` → `suspend fun clearTemptationLog()`;<br>`getWeeklySummary(): Promise<String>` → `suspend fun getWeeklySummary(): String`. | Uses the exact `greyout_schedule` key in the enforcement preference namespace. The temptation-log methods are part of this repository, not optional extras: Stage 3 analytics reads blocking data through `getTemptationLog()`. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/NativeImagePickerModule.kt` | No repository file; use the Compose/UI `rememberLauncherForActivityResult` contract with `ActivityResultContracts.PickVisualMedia` (or the Android Photo Picker equivalent). | `pickImage(): Promise<String?>` → UI launcher callback returning a nullable `Uri`; `checkMediaPermission(): Promise<boolean>` → UI/platform permission state, with the Android 13+ Photo Picker requiring no media permission and pre-33 behavior checked before launch. | This is a documented picker pattern, not a repository. Keep the URI as a URI; do not assume a filesystem path. If a later feature needs a durable local wallpaper file, copy through `ContentResolver` while the URI grant is available. |
| `artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/NativeFilePickerModule.kt` | No repository file; use UI `rememberLauncherForActivityResult` with `ActivityResultContracts.OpenDocument` and `ActivityResultContracts.CreateDocument`. `BackupManager` owns provider-backed URI I/O. | `pickFile(mimeType: String): Promise<{name: String, content: String}?>` → `OpenDocument` returns a nullable `Uri`, then `ContentResolver` reads the display name/content;<br>`saveFile(content: String, fileName: String, mimeType: String): Promise<String?>` → `CreateDocument` returns a nullable `Uri`, then `ContentResolver` writes UTF-8 content;<br>`readUri(uriString: String): Promise<String>` → `ContentResolver` read of `content://` or `file://` URI. | Do not route provider-backed `content://` URIs through app-local file APIs. The picker owns launch/cancel UI; the repository/manager owns URI I/O. |
| `artifacts/focusflow/src/services/schedulerEngine.ts` | `domain/SchedulerEngine.kt` | `detectConflicts(newTask, existingTasks)` → `SchedulerEngine.detectConflicts(newTask, existingTasks)`;<br>`findNextAvailableSlot(durationMinutes, afterTime, tasks, bufferMinutes = 5)` → `SchedulerEngine.findNextAvailableSlot(durationMinutes, afterTime, tasks, bufferMinutes = 5)`;<br>`rebalanceAfterOverrun(overrunTask, overrunMinutes, allTasks, options)` → `SchedulerEngine.rebalanceAfterOverrun(overrunTask, overrunMinutes, allTasks, options: RebalanceOptions = RebalanceOptions())`;<br>`insertTaskSafe(newTask, existingTasks)` → `SchedulerEngine.insertTaskSafe(newTask, existingTasks)`;<br>`compressSchedule(completedTask, completedAt, allTasks)` → `SchedulerEngine.compressSchedule(completedTask, completedAt, allTasks)`;<br>`compressDeletedTaskGap(deletedTask, allTasks)` → `SchedulerEngine.compressDeletedTaskGap(deletedTask, allTasks)`;<br>`getUnfinishedOverdueTasks(tasks)` → `SchedulerEngine.getUnfinishedOverdueTasks(tasks)`;<br>`analyzeScheduleHealth(tasks)` → `SchedulerEngine.analyzeScheduleHealth(tasks)`. | Literal, Android-free algorithm port. `java.time` replaces dayjs and an injectable `Clock` makes time-dependent behavior testable. The source's overnight hour-bucket behavior and its all-record total/hour-load calculation versus skipped-filtered sorted health list are preserved rather than silently corrected. |
| `artifacts/focusflow/src/tasks/backgroundTasks.ts` | `background/BackgroundFetchWorker.kt` | `BACKGROUND_FETCH` task definition → `BackgroundFetchWorker.doWork(): Result`; registration → `BackgroundFetchWorker.enqueuePeriodic(context)` using a unique 15-minute `PeriodicWorkRequest`. The worker's adapter calls are explicit `BackgroundFetchGateway.getTasksForDate`, `fireLateStartWarning`, `scheduleTaskRemindersBatch`, `getWakeUpTime`, and `scheduleMorningDigest`. `registerOverrunCheckTask()` and the `OVERRUN_CHECK`/`NOTIFICATION_BG` task definitions have no Kotlin equivalent in this worker. | Only `BACKGROUND_FETCH` survives. `OVERRUN_CHECK` is already owned by `TaskEndAlarmReceiver`/`TaskAlarmActivity`; `NOTIFICATION_BG` is already owned by `NotificationActionReceiver`. WorkManager has no `NewData`/`NoData` result equivalent, so successful runs return `Result.success()` with `rearmedCount`. A missing gateway is an explicit failure, not a silent success. |
| `artifacts/focusflow/src/services/notificationService.ts` | `notifications/NotificationChannels.kt` + `notifications/NotificationRepository.kt` | `requestPermissions(): Promise<boolean>` → `suspend fun requestPermissions(): Boolean`;<br>`setupNotificationChannels(): Promise<void>` → `fun setupNotificationChannels()`;<br>`scheduleTaskReminders(task): Promise<void>` → `suspend fun scheduleTaskReminders(task: Task)`;<br>`scheduleTaskRemindersBatch(tasks): Promise<void>` → `suspend fun scheduleTaskRemindersBatch(tasks: List<Task>)`;<br>`cancelTaskReminders(taskId): Promise<void>` → `suspend fun cancelTaskReminders(taskId: String)`;<br>`cancelTaskRemindersBatch(taskIds): Promise<void>` → `suspend fun cancelTaskRemindersBatch(taskIds: List<String>)`;<br>`cancelAllReminders(): Promise<void>` → `suspend fun cancelAllReminders()`;<br>`cancelAllRemindersExcept(taskId): Promise<void>` → `suspend fun cancelAllRemindersExcept(taskId: String)`;<br>`dismissPersistentNotification(): Promise<void>` → `suspend fun dismissPersistentNotification()`;<br>`scheduleStandaloneBlockExpiry(untilMs, blockedCount): Promise<void>` → `suspend fun scheduleStandaloneBlockExpiry(untilMs: Long, blockedCount: Int)`;<br>`cancelStandaloneBlockExpiry(): Promise<void>` → `suspend fun cancelStandaloneBlockExpiry()`;<br>`scheduleMorningDigest(profile, tasks): Promise<void>` → `suspend fun scheduleMorningDigest(profile: NotificationUserProfile?, tasks: List<Task>)`;<br>`cancelMorningDigest(): Promise<void>` → `suspend fun cancelMorningDigest()`;<br>`scheduleWeeklyReport(profile, enabled): Promise<void>` → `suspend fun scheduleWeeklyReport(profile: NotificationUserProfile?, weeklyReportEnabled: Boolean)`;<br>`cancelWeeklyReport(): Promise<void>` → `suspend fun cancelWeeklyReport()`;<br>`fireLateStartWarning(task, minutesLate): Promise<void>` → `suspend fun fireLateStartWarning(task: Task, minutesLate: Int)`. | Preserves exactly three JS channels: `task-reminders` HIGH, `morning-digest` DEFAULT, and `weekly-report` DEFAULT. The persistent foreground-service notification stays native-owned. Generic future notification delivery is an explicit `NotificationScheduler` adapter; requests are not silently discarded. Android 13 permission requests remain an ActivityResult/UI responsibility while the repository reports current permission state. |
| `artifacts/focusflow/src/services/backupService.ts` | `data/repository/BackupManager.kt` | `buildBackupJson(settings, appVersion?): Promise<string>` → `suspend fun buildBackupJson(settings: JSONObject, appVersion: String?): String`;<br>`exportBackup(settings, appVersion?): Promise<{ok, path?, error?}>` → `suspend fun exportBackup(settings: JSONObject, appVersion: String?, destinationUri: Uri): BackupFileResult`;<br>`parseBackupJson(text)` → `fun parseBackupJson(text: String): BackupParseResult`;<br>`buildRestoreCallbacks(inputs, replaceTasks?)` → `fun buildRestoreCallbacks(inputs: RestoreCallbackInputs, replaceTasks: Boolean): RestoreCallbacks`;<br>`pickAndImportBackup(callbacks)` → `suspend fun pickAndImportBackup(sourceUri: Uri, callbacks: RestoreCallbacks): RestoreResult`;<br>`restoreFromJson(text, callbacks)` → `suspend fun restoreFromJson(text: String, callbacks: RestoreCallbacks): RestoreResult`. | `FocusFlowBackupV1`, version 1, field names, portable-settings omission list, preset sections, task rows, and summary counts remain backward-compatible. SAF uses `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`; `ContentResolver` handles provider-backed `content://` URIs. ActivityResult launchers own picker UI, while `BackupManager` owns URI I/O and restore logic. |

## Complete flag ledger

These flags are intentionally listed together so the Stage 3 recipient does not
need to recover them from the individual prompt reports.

### Prompt 1 — `SharedPrefsModule.kt` → `SettingsRepository.kt`

- The legacy preference namespace is `focusday_prefs`; changing it would make
  already-installed enforcement components stop seeing existing state.
- The key audit found two daily-allowance representations:
  `daily_allowance_config` is the rich JSON configuration, while
  `daily_allowance_packages` is the legacy package-list key. Both are retained.
  The enforcement source comments describe the legacy key as “no longer
  written,” but the existing bridge method still writes it; this inconsistency
  must be resolved by the next stage rather than silently normalized here.
- Enforcement-owned keys not written by this bridge remain outside this
  repository, including `active_session_open_at_ms`,
  `active_session_last_checkpoint_ms`, `daily_allowance_usage_stats_sync`,
  `net_block_self_heal`, `net_block_global`, and related VPN status/generation
  keys. Their presence is not permission to delete or rename them.
- PIN-gated user paths and PIN-free authorized system paths are intentionally
  separate. `publishFocusSnapshotInternal` is required for completion, skip, and
  orphan cleanup and must not be routed through the user-facing PIN gate.
- Snapshot writes retain synchronous `commit()` semantics and reject on a failed
  commit; ordinary preference updates retain asynchronous `apply()` semantics.
- The source's behavior is preserved even where it may deserve a later review:
  `setFocusBreak(false, ...)` re-enables `focus_active`, inactive snapshots clear
  task fields, and malformed per-package allowance JSON resets the usage map.

### Prompt 2 — `ForegroundServiceModule.kt` and `ForegroundLaunchModule.kt`

- `ForegroundServiceController` is intentionally thin. The service remains the
  owner of notification, focus, break, fallback-enforcement, and lifecycle
  behavior.
- The controller reads the real `ForegroundTaskService` action and extra
  constants. It must not introduce guessed action strings.
- `stopServiceInternal` is deliberately separate from `stopService` and bypasses
  the session PIN only for authorized task completion, skip, and orphan-session
  reconciliation.
- Battery-optimization exemption is best-effort. The old bridge resolved even
  if every OEM settings intent failed, so the Kotlin controller preserves that
  behavior.
- `LauncherController.showOverlay` is still a front-of-app placeholder. The
  full-screen lock overlay remains deferred and needs its own activity,
  manifest/config-plugin work, permission/intent choice, back handling, and
  focus-end dismissal.

### Prompt 3 — `BlockOverlayModule.kt`, `AversionsModule.kt`, and `NuclearModeModule.kt`

- Block-overlay configuration is separate from overlay enforcement. The
  controller does not own `BlockOverlayActivity`; callers that launch the
  overlay must keep the source `Settings.canDrawOverlays()` permission gate.
- Quote JSON validation, empty-array reset behavior, and wallpaper
  existence/readability checks are preserved. No invalid path should be stored.
- Aversions settings are partial updates. An omitted field must remain
  unchanged, and changing the weekly-report field still schedules/cancels the
  native report alarm.
- The uninstall batch keeps the source's 500 ms sequencing and per-dialog
  failure swallowing, but uses coroutine `delay` rather than `Handler`.
  `isAppInstalled` still reports package presence, not whether the system dialog
  was confirmed.

### Prompt 4 — installed apps, greyout, and picker patterns

- Returning `Drawable` directly from `InstalledAppsRepository` is deliberate;
  base64 icon encoding was only a React Native bridge workaround. Stage 3 must
  not recreate the old encoding unless a separate UI boundary requires it.
- Package visibility remains an Android 11+ concern. The repository still
  includes launcher-visible applications and IMEs, excludes FocusFlow itself,
  and does not use the `FLAG_SYSTEM` heuristic.
- `GreyoutRepository` keeps the exact `greyout_schedule` key and owns all three
  temptation-log methods because analytics depends on them.
- Image picking is a UI `rememberLauncherForActivityResult` pattern, not a
  repository. Android 13+ Photo Picker needs no media permission; pre-33 uses
  the platform media permission path.
- File picking is a UI `OpenDocument`/`CreateDocument` pattern, not a
  repository. Provider-backed `content://` URIs must be read and written with
  `ContentResolver`, not treated as app-local paths.

### Prompt 5 — `schedulerEngine.ts` and `backgroundTasks.ts`

- `SchedulerEngine` is intentionally pure Kotlin/JVM logic with no Android,
  Room, WorkManager, or notification dependency. The injected `Clock` is only
  for deterministic time behavior; default behavior uses the system clock.
- The source's overnight hour-bucket behavior is preserved, including the
  empty range when end hour is numerically earlier than start hour.
- The source's health calculation is preserved even though it totals all task
  records while the sorted health list filters skipped tasks. This is flagged,
  not silently corrected.
- Only `BACKGROUND_FETCH` is ported. `FOCUS_OVERRUN_CHECK`/`OVERRUN_CHECK` is
  already duplicated by `TaskEndAlarmReceiver`/`TaskAlarmActivity`, and
  `TASK_NOTIFICATION_BG`/`NOTIFICATION_BG` is already owned by
  `NotificationActionReceiver`; porting them again would create duplicate
  execution paths.
- WorkManager cannot return Expo's `NewData`/`NoData` distinction. Successful
  work therefore returns `Result.success()` with `rearmedCount`; failures are
  explicit. A missing `BackgroundFetchGateway` is also an explicit failure.

### Prompt 6 — `notificationService.ts` and `backupService.ts`

- Notification channels remain exactly `task-reminders` HIGH,
  `morning-digest` DEFAULT, and `weekly-report` DEFAULT.
- The existing native-owned channels
  `focusday_foreground`, `focusday_block_alert`, `focusday_vpn`, and
  `task_alarm` are not recreated; changing their importance or alarm behavior
  would be a migration regression.
- The persistent foreground notification is native-owned, so
  `dismissPersistentNotification` remains a no-op compatibility shim.
- Android 13 notification permission prompting belongs to ActivityResult/UI;
  the repository can only report current permission state.
- Generic notification scheduling is an explicit adapter boundary in the
  Kotlin scaffold. It must not silently claim to have delivered a request
  without an installed scheduler.
- The backup envelope must stay byte-shape compatible at the semantic JSON
  level: `kind: "FocusFlowBackupV1"`, `version: 1`, field names, task rows,
  preset sections, summary counts, and the portable-settings omission list.
- `focusMirrorVpnEnabled` remains a boolean and defaults to `false`.
  Live enforcement switches and standalone runtime state stay device-local
  during restore; the importing device's current values are merged back in.
- SAF owns real storage destinations. Export uses `ACTION_CREATE_DOCUMENT`;
  import uses `ACTION_OPEN_DOCUMENT`; both support provider-backed
  `content://` URIs rather than assuming filesystem paths.
- Existing `.focusflow` and legacy `.json` backups remain importable. Replacement
  restore is refused while a focus session is active, historical scheduled
  tasks whose end time has passed become skipped, and historical/resolved tasks
  are imported without alarms.