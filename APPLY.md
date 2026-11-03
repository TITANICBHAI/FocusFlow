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

STATS EXTRAS (findings) — added in the latest round:
  NEW   analytics/detection/DetectorEligibility.kt   which apps the "habit" findings may talk about
  NEW   analytics/detection/BehaviourDetectors.kt    LATE_NIGHT_USE + HEAVY_DAILY_USE detectors
  EDIT  analytics/detection/ManipulationDetectors.kt (eligibility filters, plain copy)
  EDIT  analytics/detection/FindingDetectors.kt      (plain copy)
  EDIT  analytics/detection/UsageDetectorWindows.kt, FindingDetectionRunner.kt
  NEW   ui/stats/FindingActions.kt                   "Block <App> now" button on app findings
  EDIT  ui/stats/FindingCardView.kt, FindingsSection.kt, StatsInsightsExperience.kt
  EDIT  ui/stats/StatsViewModel.kt                   question answers now change the finding
  NEW   tests: DetectorEligibilityTest, BehaviourDetectorsTest, FindingActionsTest

HABIT HEADS-UP + USAGE-FIRST STATS — latest round:
  NEW   notifications/HabitNudgePlanner.kt, HabitNudgeScheduler.kt, receivers/HabitNudgeReceiver.kt
  EDIT  notifications/NotificationChannels.kt (new "Habit heads-ups" channel)
  EDIT  AndroidManifest.xml (HabitNudgeReceiver)  — this file also carries the widget change from before
  EDIT  FocusFlowApp.kt, enforcement/receivers/BootReceiver.kt, background/UsageMaintenanceWorker.kt (re-plan hooks)
  EDIT  analytics/detection/BehaviourDetectors.kt (heavy-use finding records its peak hour)
  NEW   ui/stats/StatsReadiness.kt (missing-permission card + heads-up switch), ui/stats/PhoneTimeCard.kt
  EDIT  ui/stats/StatsInsightsExperience.kt, FindingsSection.kt, EmptyStatsState.kt
  NEW   tests: HabitNudgePlannerTest, StatsReadinessTest (+1 case in BehaviourDetectorsTest)

Not compiled here (no Kotlin/Gradle in my sandbox): run ./gradlew :app:testDebugUnitTest once.
After installing, remove and re-add the widget on the home screen.
