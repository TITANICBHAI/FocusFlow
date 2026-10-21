# FocusFlow phase code-work inventory

**Audit basis:** the initial phase inventory was audited at `3baa713` on 2026-10-21; Phase 9 and Phase 5 updates were later applied against the clean `a8cd6fc` base.

This is a code-file inventory, not a progress tracker. It excludes tests, builds, lint, device checks, and other verification work. `exercise/TRACKER.md` remains the authoritative progress record.

## Phase 0 — Baseline

**Code work:** None. This phase is baseline-only.

## Phase 1 — Model, database, repository

**Code work:** No remaining implementation work identified in the current source.

**Existing implementation:** `Task.kt`, `Subtask.kt`, `TaskEntity.kt`, `TaskDao.kt`, `FocusFlowDatabase.kt`, `AppModule.kt`, `TaskRepository.kt`, `SubtaskJsonCache.kt`, `TaskViewModel.kt`, and the Room schema for version 10.

## Phase 2 — Format, import/export, images at rest

**Code work:** No remaining implementation work identified in the current source.

**Existing implementation:** `BackupJsonLimits.kt`, `BackupEnvelope.kt`, `BackupSerializer.kt`, `SubtaskParser.kt`, `ImageCodec.kt`, `SubtaskImageStore.kt`, `BackupRestoreEngine.kt`, `SubtaskAssetExporter.kt`, `BackupViewModel.kt`, `ImportConfirmScreen.kt`, `SettingsScreen.kt`, `AppModule.kt`, and `FocusFlowApp.kt`.

## Phase 3 — AI prompt and format guide

**Code work:** No remaining in-repository implementation work identified.

**Existing implementation:** `FocusFlowAgentGuide.kt`, `FocusFlowFormatGuideData.kt`, `FocusFlowFormatGuideContent.kt`, and `FocusFlowFileGuideScreen.kt`.

**Outside the repository:** The hosted format-guide page cannot be edited through these source files.

## Phase 4 — Viewing UI

**Code work:** The planned viewing, progress, navigation, and image-display code appears present; no additional implementation work identified by this source audit.

**Existing implementation:** `HomeScreen.kt`, `TaskCard.kt`, `TaskDetailModal.kt`, `ui/home/subtask/SubtaskComponents.kt`, `ui/home/subtask/SubtaskDetailScreen.kt`, `ui/common/SubtaskImage.kt`, `RouteTextScaleContext.kt`, `TextSizeSection.kt`, and `TaskViewModel.kt`.

## Phase 5 — Enhancements

**Code work:** Source now includes Photo Picker attach/replace/remove for item and step images, bounded image-store ingestion, a full-screen zoom viewer, local rest/duration timers, keep-screen-on while the subtask screen is visible, and the privacy-policy sentence. Confirmed task-level and item-level progress-reset dialogs were already present.

**Files changed or added:** `data/images/SubtaskImageStore.kt`, `ui/home/subtask/SubtaskDetailScreen.kt`, `ui/home/subtask/SubtaskPhotoManagement.kt`, `ui/home/subtask/SubtaskPhotoAttachment.kt`, `ui/home/subtask/SubtaskImageZoomViewer.kt`, `ui/home/subtask/SubtaskTimerState.kt`, `ui/home/subtask/SubtaskTimerPanel.kt`, `ui/home/TaskDetailModal.kt`, `ui/home/HomeScreen.kt`, and `ui/legal/PrivacyPolicyScreen.kt`.

**Optional within this phase:** Add entry points from `ui/active/ActiveScreen.kt` and `ui/focus/ActiveBlockScreen.kt`; keep those limited to opening the existing subtask UI.

## Phase 6 — Help, changelog, release metadata

**Code work still needed:**

- Add the subtask usage guidance to the shared help content and the Settings help entry point. Include ticking, sets/rest, photos, and the fact that completing subtasks does not complete the task.
- Add the release note for subtasks, images, and bundles.
- Update the app version to version code `16` and version name `1.1.5`.

**Files to change:** `ui/support/HowToUseScreen.kt`, `ui/settings/SettingsHowToUseScreen.kt`, `assets/changelog.json`, and `app/build.gradle.kts`.

The format-guide and generation-prompt source files already contain bundle, version 2, and `repsMax` guidance. The hosted guide remains outside the repository.

## Phase 7 — In-app authoring (optional)

**Code work:** Not part of the required phases; do this only if separately requested.

**Likely files if authorized:** Add an authoring composable under `ui/home/subtask/` for adding, editing, reordering, deleting, and duplicating subtasks; connect it from `TaskDetailModal.kt` or `SubtaskComponents.kt` and persist through `TaskViewModel.replaceSubtasks`.

## Phase 8 — Hardening

**Code work:** No remaining implementation work identified in the current source audit.

**Existing implementation:** `SubtaskParser.kt`, `UnknownFieldWarnings.kt`, `BackupJsonLimits.kt`, `BackupSerializer.kt`, `ImageCodec.kt`, `SubtaskImageStore.kt`, `BackupRestoreEngine.kt`, `Subtask.kt`, `FocusFlowAgentGuide.kt`, and `FocusFlowFormatGuideData.kt` contain the fail-soft parsing, unknown-field warnings, budgets, EXIF/fidelity handling, bounded restore, version 1/2 behavior, `repsMax`, and generated guidance changes.

## Phase 9 — Hybrid ZIP bundle

**Code work:** No remaining implementation work identified by this source audit. Removed the unused `SubtaskImageStore.reencodeForExport` helper and the unused image-store constructor parameter from `SubtaskAssetExporter`; export reads stored image bytes directly through the bundle writer.

**Existing implementation:** Container sniffing, bounded ZIP staging/reading, `AssetByteSource`, bundle asset/hash handling, restore wiring, bundle writing, container-aware export UI, import summary, and guide/prompt updates appear present in `BackupSource.kt`, `AssetByteSource.kt`, `BackupSerializer.kt`, `BackupRestoreEngine.kt`, `BackupBundleWriter.kt`, `BackupFileManager.kt`, `BackupViewModel.kt`, `ImportConfirmScreen.kt`, `BackupExportNoticeDialog.kt`, `SettingsScreen.kt`, `FocusFlowApp.kt`, `AppModule.kt`, `FocusFlowAgentGuide.kt`, and the format-guide files.
