# Stage 5 migration report

| Source file | Target file | Notes on what was ported / deliberately dropped | Flags |
|---|---|---|---|
| `artifacts/focusflow/src/services/schedulerEngine.ts` | `app/src/main/java/com/tbtechs/focusflow/domain/SchedulerEngine.kt` | Ported `detectConflicts`, `findNextAvailableSlot`, `rebalanceAfterOverrun`, `insertTaskSafe`, `compressSchedule`, `compressDeletedTaskGap`, `getUnfinishedOverdueTasks`, and `analyzeScheduleHealth`. The Kotlin model preserves the source task/status/priority/reminder fields and returns typed equivalents of `ConflictResult`, `RebalanceResult`, `OverrunResult`, and `ScheduleHealth`. | No Android dependency. `java.time` replaces dayjs. A `Clock` constructor parameter makes timestamp-based behavior deterministic in unit tests while the default remains the system clock. The source's overnight hour-bucket behavior is intentionally preserved, including its empty range when the end hour is numerically earlier than the start hour. The source also sums total minutes and hour load across all task records even though its sorted health list filters skipped tasks; this was preserved and flagged rather than corrected. |
| `artifacts/focusflow/src/tasks/backgroundTasks.ts` | `app/src/main/java/com/tbtechs/focusflow/background/BackgroundFetchWorker.kt` | Ported only `BACKGROUND_FETCH`: UTC task-date lookup, late-start warnings for scheduled tasks 3–15 minutes late, skipping ended tasks, batched reminder re-arming, evening morning-digest scheduling, and a 15-minute unique `PeriodicWorkRequest`. A `BackgroundFetchGateway` is the explicit adapter boundary for the future Room and notification repositories. | `FOCUS_OVERRUN_CHECK` / `OVERRUN_CHECK` was deliberately dropped because task-end handling already belongs to `TaskEndAlarmReceiver` and `TaskAlarmActivity`. `TASK_NOTIFICATION_BG` / `NOTIFICATION_BG` was deliberately dropped because background notification actions already belong to `NotificationActionReceiver`. The worker fails explicitly until application startup installs its gateway; it does not silently report success without doing the work. WorkManager has no direct `NewData`/`NoData` result equivalent, so successful runs always return `Result.success()` with `rearmedCount` output. |

## Public scheduler API mapping

| TypeScript export | Kotlin member |
|---|---|
| `detectConflicts(newTask, existingTasks)` | `SchedulerEngine.detectConflicts(newTask, existingTasks)` |
| `findNextAvailableSlot(durationMinutes, afterTime, tasks, bufferMinutes)` | `SchedulerEngine.findNextAvailableSlot(durationMinutes, afterTime, tasks, bufferMinutes)` |
| `rebalanceAfterOverrun(overrunTask, overrunMinutes, allTasks, options)` | `SchedulerEngine.rebalanceAfterOverrun(overrunTask, overrunMinutes, allTasks, RebalanceOptions)` |
| `insertTaskSafe(newTask, existingTasks)` | `SchedulerEngine.insertTaskSafe(newTask, existingTasks)` |
| `compressSchedule(completedTask, completedAt, allTasks)` | `SchedulerEngine.compressSchedule(completedTask, completedAt, allTasks)` |
| `compressDeletedTaskGap(deletedTask, allTasks)` | `SchedulerEngine.compressDeletedTaskGap(deletedTask, allTasks)` |
| `getUnfinishedOverdueTasks(tasks)` | `SchedulerEngine.getUnfinishedOverdueTasks(tasks)` |
| `analyzeScheduleHealth(tasks)` | `SchedulerEngine.analyzeScheduleHealth(tasks)` |

## Direct dependency notes

- `SchedulerEngine` depends only on Kotlin/JVM time and collection types. It does not reference Android, Room, WorkManager, or a repository.
- `BackgroundFetchWorker` depends on WorkManager and a `BackgroundFetchGateway`. The gateway maps the old `dbGetTasksForDate`, `fireLateStartWarning`, `scheduleTaskRemindersBatch`, `dbGetSettings`, and `scheduleMorningDigest` calls to the future Kotlin persistence/notification layer.