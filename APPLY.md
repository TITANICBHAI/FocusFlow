# FocusFlow patch — copy over the repo root (paths are relative to repo root)

REPLACED files (full file):
  app/src/main/AndroidManifest.xml                                  (widget receiver: 3 extra actions)
  app/src/main/java/com/tbtechs/focusflow/MainActivity.kt           (fromLink + navigate on notification tap)
  app/src/main/java/com/tbtechs/focusflow/di/AppModule.kt           (widget refresh on task changes)
  app/src/main/java/com/tbtechs/focusflow/notifications/receivers/ReminderReceiver.kt
  app/src/main/java/com/tbtechs/focusflow/ui/navigation/Routes.kt   (Routes.fromLink)
  app/src/main/java/com/tbtechs/focusflow/ui/home/TaskDetailModal.kt
  app/src/main/java/com/tbtechs/focusflow/ui/home/subtask/PhotoDownloadController.kt
  app/src/main/java/com/tbtechs/focusflow/ui/home/subtask/PhotoSlotsBanner.kt
  app/src/main/java/com/tbtechs/focusflow/data/model/SubtaskPhotoSlots.kt
  app/src/main/java/com/tbtechs/focusflow/widget/FocusFlowWidget.kt
  app/src/main/java/com/tbtechs/focusflow/ui/backup/BackupViewModel.kt        (export skips missing pictures)
  app/src/main/java/com/tbtechs/focusflow/data/backup/BackupBundleWriter.kt   (readableAssets)
  app/src/main/res/layout/widget_focusflow.xml
  app/src/main/res/drawable/widget_background.xml
  app/src/main/res/xml/widget_info.xml
  app/src/test/.../ui/navigation/RoutesTest.kt

NEW files:
  app/src/main/java/com/tbtechs/focusflow/widget/WidgetSnapshot.kt
  app/src/main/res/drawable/widget_progress.xml
  app/src/main/res/values/widget_colors.xml
  app/src/main/res/values-night/widget_colors.xml
  app/src/test/.../widget/WidgetSnapshotBuilderTest.kt
  app/src/test/.../data/model/SubtaskPhotoSlotsSummaryTest.kt

Export fix: a picture whose file is gone no longer aborts the backup. It is left out, its
description is kept as a 'photo needed' slot, and the success dialog shows a warning.

Not compiled here (no Kotlin/Gradle in my sandbox): run ./gradlew :app:testDebugUnitTest once.
After installing, remove and re-add the widget on the home screen.
