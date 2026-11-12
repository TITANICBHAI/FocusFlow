# FocusFlow – launcher redesign, image fixes, guide update

Copy the folder `FocusFlow-main/` over your project (paths are the same as in the repo).

## Delete these files (no longer used)
- app/src/main/java/com/tbtechs/focusflow/ui/home/subtask/PhotoPromptDialog.kt
- app/src/main/java/com/tbtechs/focusflow/ui/home/subtask/PhotoPromptBuilder.kt
- app/src/test/java/com/tbtechs/focusflow/ui/home/subtask/PhotoPromptBuilderTest.kt

## Not compiled with Gradle
LauncherActivity.kt was type-checked against the Android API with stubs for androidx.
The Compose files (LauncherSetupScreen, PhotoBatchPicker, PhotoSlotsBanner, TaskDetailModal)
were only syntax-checked. Please run a Gradle build and the unit tests.
