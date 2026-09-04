# FocusFlow Task System Audit

**Audit date:** September 4, 2026  
**Scope:** `artifacts/focusflow/` task lifecycle and all connected persistence, scheduling, notification, background, focus-session, backup, and UI paths  
**Status:** Audit complete; repair plan below  
**Audited tree:** Includes the current task-operation queue and contiguous-slot compression fix in the working tree. Those fixes have source-level validation, but the project test runner could not execute because this checkout has no installed `node_modules`.

## 1. Executive conclusion

The task system is currently a distributed state machine without one authoritative command boundary.

The same task can be represented in:

1. React state in `AppContext`.
2. SQLite rows in `src/data/database.ts`.
3. Expo scheduled notifications.
4. Native Android `AlarmManager` alarms.
5. Native focus/foreground-service state.
6. Headless background-task state.
7. Backup/import state.

Those representations are updated by several different implementations of “complete,” “skip,” “extend,” “delete,” and “refresh.” Some paths use the scheduler; some bypass it. Some paths update the database first, some update React first, and some update native state asynchronously. The result is not just cosmetic drift: a later read or background callback can legitimately write an older task version back over a newer one.

### Most likely explanation for the reported symptom

The reported behavior—skipping several tasks and later seeing one return as if it was never skipped—has two direct causes in the current design:

1. **Refresh/mutation race:** a refresh could read the database before a skip committed and dispatch the old snapshot after the skip had updated the UI.
2. **Back-to-back mutation stale snapshot:** React dispatch is asynchronous. Multiple queued task operations could read the pre-dispatch task list and write an earlier task version back to SQLite.

The working tree now contains a shared AppContext task-operation queue plus a synchronous task snapshot to contain those two foreground races. This is a containment fix, not the complete task-system repair: headless/background writers, database fallback behavior, native alarm races, and duplicated lifecycle paths still remain.

## 2. System map

| Area | Current owner/path | What it does | Consistency concern |
|---|---|---|---|
| Task model | `src/data/types.ts` | Defines `TaskStatus`, task fields, timestamps, reminders | `active` and `overdue` exist but do not have one consistent lifecycle meaning |
| React task cache | `src/context/AppContext.tsx` | Holds the task slice used by screens and orchestration | Can diverge from SQLite and is updated through multiple action styles |
| Durable task store | `src/data/database.ts` | SQLite task rows and batch updates | Some failures become empty results; recovery DB can become a second canonical store |
| Pure task helpers | `src/services/taskService.ts` | Query, status, shift, formatting helpers | Boundary/status semantics are not fully defined |
| Scheduler | `src/services/schedulerEngine.ts` | Conflict detection, insertion, rebalancing, compression | Implemented helpers are not consistently called; several edge cases violate stated intent |
| Foreground commands | `src/context/AppContext.tsx` | Add/edit/complete/skip/extend/delete | Each command has different persistence and side-effect ordering |
| Reminders | `src/services/notificationService.ts` | Expo notifications and native alarm bridge | Native alarm scheduling is fire-and-forget |
| Headless commands | `src/tasks/backgroundTasks.ts` | Overrun, background fetch, notification actions | Reads/writes directly to DB outside AppContext command ordering |
| Focus coupling | `src/services/focusService.ts`, `AppContext.tsx` | Focus session, foreground service, SharedPreferences | Delete/bulk paths can leave active native state behind |
| UI callers | `app/(tabs)/index.tsx`, `focus.tsx`, settings, modals | User actions and confirmation dialogs | UI actions do not all use the same command semantics |
| Import/export | `src/services/backupService.ts` | Task backup and restore | Restore is not one transaction and has duplicate/partial-import risks |

## 3. Lifecycle audit

### 3.1 Create

`AppContext.addTask` directly inserts the task, appends it to React state, and schedules reminders.

The scheduler exposes `detectConflicts` and `insertTaskSafe`, but the create path does not call them. A newly created task can overlap existing tasks even though the scheduler advertises conflict-free insertion. The edit path has the same problem.

**Finding T-01 — P1: scheduler insertion is disconnected from task creation/editing**

- **Evidence:** `src/context/AppContext.tsx` `addTask` and `updateTask`; `src/services/schedulerEngine.ts` `detectConflicts` and `insertTaskSafe`.
- **Impact:** Overlapping tasks enter the database as valid-looking rows. Later completion, extension, and compression calculations then operate on an already-invalid schedule.
- **Repair:** Define one insertion/edit policy, call it from the command layer, and persist the complete affected set in one transaction. Do not expose scheduler helpers as if they are active when callers bypass them.

**Finding T-02 — P1: insert success is not verified**

- **Evidence:** `dbInsertTask` uses `INSERT OR IGNORE` and does not expose affected-row information; `addTask` dispatches the task unconditionally.
- **Impact:** A duplicate ID or competing writer can result in React displaying a task that SQLite did not insert.
- **Repair:** Use an explicit insert result or reject ignored inserts. IDs and imported duplicates must be validated before the command reports success.

### 3.2 Edit

`updateTask` replaces one row and reschedules reminders. It does not use conflict validation or rebalancing. Editing an active task has extra focus/notification synchronization, but native alarm scheduling remains asynchronous.

**Finding T-03 — P1: edits can invalidate the schedule**

- **Impact:** Changing a start time, end time, or duration can introduce overlaps without a user decision or peer-task update.
- **Repair:** Treat edit as a schedule command, not a single-row update. Recalculate affected peers and persist the full result atomically.

**Finding T-04 — P1: active edit can leave an old native end alarm**

- **Evidence:** `src/services/notificationService.ts` schedules `TaskAlarmModule.scheduleAlarm` with `void` rather than awaiting the native result.
- **Impact:** The task row and foreground notification can show a new end time while an old native alarm still fires.
- **Repair:** Make native alarm scheduling awaitable and serialize it with cancellation. A task mutation should not report complete until its required alarm operation has a defined result.

### 3.3 Complete

The explicit complete path:

1. Reads the current task snapshot.
2. Marks the task complete.
3. Compresses later unresolved tasks when completion is early.
4. Batch-updates SQLite.
5. Cancels and re-arms reminders.
6. Cancels/dismisses the task alarm.
7. Dispatches React state.
8. May stop focus or keep it alive until the original end.

This is the most complete foreground path, but it still spans multiple systems without a shared transaction or compensating recovery.

**Finding T-05 — P0: database success and side-effect success are not one command result**

- **Evidence:** `AppContext.completeTask`, `notificationService`, native alarm calls.
- **Impact:** SQLite can say “completed” while notification cancellation, alarm cancellation, focus teardown, or React reconciliation fails. The caller receives an error after the durable task mutation already happened.
- **Repair:** Split the command into durable mutation plus explicit reconciliation. Persist the task result first, then reconcile side effects idempotently; on side-effect failure, record/retry reconciliation rather than pretending the entire task command failed.

**Finding T-06 — P1: completion compression policy is not fully specified**

- **Impact:** Exact-touching tasks, existing gaps, overlaps, and resolved peer tasks do not have a documented uniform policy.
- **Repair:** Define whether compression is based on reserved slots or actual occupied time, then encode that policy in pure functions and property tests.

### 3.4 Skip

The explicit skip path now uses the AppContext operation queue and task snapshot. It:

1. Reads the synchronous latest task snapshot.
2. Marks one task skipped.
3. Compresses later unresolved tasks when appropriate.
4. Batch-updates SQLite.
5. Cancels reminders and the native alarm.
6. Dismisses the visible alarm.
7. Dispatches the resulting task snapshot.
8. Stops focus if the skipped task owns the active session.

The operation queue fixes a real foreground ordering problem, but it does not cover headless writers or make all side effects transactional.

**Finding T-07 — P0: stale refreshes could resurrect skipped tasks**

- **Before containment:** `refreshTasks` and task mutations could overlap. The older refresh result could be dispatched after the skip.
- **Current containment:** `src/services/taskOperationQueue.ts`, `taskOperationsRef`, and `taskSnapshotRef` in `AppContext` serialize foreground reads/mutations and ensure back-to-back operations see the previous operation’s result.
- **Remaining risk:** Background/headless database writes are outside this queue. There is no revision/version check that prevents a headless stale snapshot from overwriting a newer foreground command.
- **Required regression test:** Deferred refresh + skip, two rapid skips, refresh during skip, and headless/foreground overlap.

**Finding T-08 — P1: skipped-task compression had an exact-boundary defect**

- **Before containment:** `compressSchedule` excluded a task whose start exactly equaled the skipped task’s end.
- **Current containment:** The compression condition now includes the exact end boundary, and a regression test covers a contiguous next task.
- **Remaining risk:** `compressDeletedTaskGap` still needs the same explicit boundary policy and tests.

### 3.5 Bulk unresolved-task resolution

The “Skip All” / “Mark All Done” alert is a separate implementation inside an effect. It does not call `completeTask` or `skipTask`.

The current path now uses the task queue, resolves against the latest task snapshot, cancels reminders for terminal tasks, and avoids overwriting a task already completed/skipped by another action. It still does not perform all normal lifecycle work.

**Finding T-09 — P0: bulk resolution is not equivalent to individual resolution**

- **Missing or divergent behavior:** no schedule compression, no explicit full-screen alarm dismissal, no active-focus teardown, no daily-completion update, and no shared completion/skip policy.
- **Impact:** Bulk skip can leave stale alarms, stale focus enforcement, stale gaps, and inconsistent statistics.
- **Repair:** Implement one reusable `resolveTasks` command that accepts a set of IDs and a terminal status. Individual and bulk actions must call that command; only the confirmation UI should differ.

**Finding T-10 — P0: failed bulk resolution can suppress future recovery**

- **Evidence:** IDs are added to `alertedUnresolvedRef` before the user action completes.
- **Impact:** If save fails, the task remains unresolved but will not prompt again during the provider lifetime. “Keep Working” also suppresses future prompts even though the task remains unresolved.
- **Repair:** Track prompt state separately from resolution. Remove an ID from the “alerted” set after failure, and define whether “Keep Working” means “remind later” or “suppress until a new event.”

### 3.6 Extend

Extension uses `rebalanceAfterOverrun`, batch-updates the extended task and returned peers, schedules reminders, updates foreground/SharedPreferences state, and shows alerts for critical/auto-skipped tasks.

**Finding T-11 — P0: critical-task handling can commit an overlap**

- **Evidence:** `rebalanceAfterOverrun` places a critical task in `needsUserConfirm`, resets cumulative shift, and leaves later tasks in their original slots. `extendTaskTime` persists the result before the user confirms.
- **Impact:** The app can save a schedule that is known to overlap, then only tell the user afterward.
- **Repair:** Either reject/defer the extension until the critical conflict is resolved, or persist an explicit conflict state that prevents the schedule from being treated as valid.

**Finding T-12 — P1: overrun shifting ignores gaps**

- **Impact:** A predecessor overrun can move every later task by the full overrun even when an existing gap absorbs it. Repeated extensions cause avoidable schedule drift and alarm churn.
- **Repair:** Recalculate from an occupied-until cursor and only shift when the predecessor’s new end crosses the next task’s start.

**Finding T-13 — P1: auto-skip accounting is not slot-aware**

- **Impact:** Subtracting only a skipped task’s duration does not account for gaps, overlaps, or anchor tasks. Later tasks can still overlap or move by the wrong amount.
- **Repair:** Recompute the schedule from ordered intervals rather than adjusting one cumulative number without checking actual placement.

### 3.7 Delete

Deleting a future scheduled task attempts to compress the deleted slot and updates shifted peers. The database delete and peer update are separate database operations.

**Finding T-14 — P0: deleting an active/focused task can orphan enforcement**

- **Evidence:** `deleteTask` does not check the active focus session before deleting; the UI permits deletion through the edit modal.
- **Impact:** Native focus, foreground service, SharedPreferences, and a focus-session row can remain active after the task row is gone.
- **Repair:** Apply the same safety boundary as “Clear All Tasks”: require the configured session credential when needed, stop/close the active focus session first, then delete the task and reconcile native state.

**Finding T-15 — P1: delete is not atomic with schedule compression**

- **Evidence:** `dbDeleteTask` runs before `dbUpdateTasksBatch(shifted)`.
- **Impact:** A failure between operations permanently deletes the task but leaves peer tasks in the old positions.
- **Repair:** Add a database command that deletes the row and updates shifted peers in one SQLite transaction. Check affected rows.

**Finding T-16 — P1: delete-gap boundary behavior is undefined**

- **Evidence:** `compressDeletedTaskGap` only shifts tasks strictly after the deleted end.
- **Impact:** A contiguous task starting exactly at the deleted end is not pulled into the freed slot.
- **Repair:** Choose and test the same reserved-slot boundary policy used by skip/complete.

### 3.8 Clear-all and backup/import

Settings has its own clear-all path, and backup restore has its own task insertion/deletion path. These are not built on the same task command layer.

**Finding T-17 — P0: recovery DB fallback can make tasks appear to disappear and return**

- **Evidence:** `src/data/database.ts` can open a fresh `focusday_recovery.db` after primary open failures and treat it as canonical. It later deletes the recovery file when the primary opens.
- **Impact:** A transient failure can expose an empty task database; writes can land in the empty database; the next launch can reopen the primary and make old data “come back.”
- **Repair:** Never silently activate a fresh empty database. Surface unavailable state, preserve the primary, and make repair/import explicit.

**Finding T-18 — P0: failed task reads become empty task lists**

- **Evidence:** task read helpers use `runWithDbOr(..., [])`; `refreshTasks` dispatches the result.
- **Impact:** “Database unavailable” is indistinguishable from “no tasks.” A transient read failure can clear a valid React task list.
- **Repair:** Return a typed success/error result or throw through the AppContext boundary. Render an explicit unavailable state instead of an empty schedule.

**Finding T-19 — P1: backup restore is not transactional**

- **Evidence:** `src/services/backupService.ts` deletes/imports tasks in multiple steps and can continue after partial failures.
- **Impact:** A failed restore can leave a partially deleted and partially imported database. Reminder scheduling is a later side effect with no durable retry state.
- **Repair:** Validate the entire backup first, then apply one transactional import/merge command. Keep existing data until validation and commit succeed.

**Finding T-20 — P1: duplicate IDs in imports are not consistently rejected**

- **Impact:** SQLite can ignore a duplicate while React counts/appends it as imported.
- **Repair:** Deduplicate and schema-validate the backup before any write; report exact accepted/rejected counts.

## 4. Persistence and concurrency findings

### 4.1 Foreground queue is necessary but not sufficient

The new AppContext queue correctly serializes foreground reads and writes. However:

- `src/tasks/backgroundTasks.ts` writes directly to SQLite.
- Native notification/headless actions can arrive near foreground resume.
- The SQLite write queue serializes JavaScript database writes but does not provide a cross-process logical revision.
- Two reads inside `refreshTasks` are not one consistent database snapshot.

**Required target:** every task mutation should carry a monotonic revision or compare-and-set version. A stale writer must be rejected or merged, not silently accepted.

### 4.2 Database write results are under-specified

`dbUpdateTasksBatch` does not verify affected row counts. An update of a missing/deleted task is indistinguishable from a successful update.

**Required target:** database commands return:

```text
success | not_found | conflict | unavailable | validation_error
```

Do not use exceptions and empty fallbacks as the task protocol.

### 4.3 Settings/database startup behavior can affect tasks

The broader persistence layer still has task-impacting failure modes:

- `dbGetSettings` can return defaults after failures.
- The startup timeout can mark the app ready while the original database request is still running.
- The schema migration catches every `ALTER TABLE` error, not only “column already exists.”
- The recovery database can create a second canonical store.

These are not skip-specific, but they can change reminder settings, focus policy, or the visible task list during the same session.

## 5. Scheduler correctness findings

**Finding S-01 — P1: status filters are inconsistent**

- Conflict/slot logic treats every status except `completed`/`skipped` as active.
- Notifications exclude `overdue`.
- Compression often only shifts `scheduled`.
- `getCurrentTask` treats any non-terminal status as current.

Define a single status policy:

| Status | Resolvable? | Blocks schedule? | Gets reminders? | Can own focus? |
|---|---:|---:|---:|---:|
| `scheduled` | yes | yes | yes | when active |
| `active` | yes | yes | defined explicitly | yes |
| `overdue` | yes/no — decide | defined explicitly | no/defined explicitly | defined explicitly |
| `completed` | no | no | no | no |
| `skipped` | no | no | no | no |

**Finding S-02 — P1: background task date lookup uses UTC while UI lookup uses local time**

- **Evidence:** `backgroundTasks.ts` builds date keys with `toISOString().slice(0, 10)`; `dbGetTasksForDate` interprets dates through local time.
- **Impact:** Around UTC/local midnight, a background overrun or notification action can find the wrong day or no task.
- **Repair:** Use one local-calendar-date helper everywhere, including background handlers and stats.

**Finding S-03 — P1: overrun handlers can mutate overdue tasks**

- The overrun handler rejects completed/skipped tasks but not overdue tasks.
- Startup converts unresolved expired tasks to overdue.
- A stale end event can therefore extend an overdue task.

Require an actionable status and validate the event against the current task version/end time.

**Finding S-04 — P1: schedule-health hour accounting fails across midnight**

`analyzeScheduleHealth` loops from start hour to end hour using the start day and indexes load by hour only. Cross-midnight tasks can contribute zero load, and the same clock hour on different calendar days is combined.

Use date-hour buckets or interval splitting by calendar day.

**Finding S-05 — P2: exact-time semantics are inconsistent**

Active/current/overdue/background checks use different combinations of strict `<`/`>` and inclusive comparisons. Define exact start/end behavior once and test it at:

- exact start,
- exact end,
- one millisecond before,
- one millisecond after,
- local midnight,
- DST transition.

**Finding S-06 — P2: late-start warnings can repeat**

Background fetch can fire repeatedly during the 3–15 minute window, and there is no persisted warning marker. The user can receive duplicate warnings, while the fetch path skips rearming that task’s reminder.

Persist a warning revision/date or make the notification identifier deterministic and idempotent.

## 6. Native notification and focus findings

**Finding N-01 — P0: native alarm scheduling is fire-and-forget**

`TaskAlarmModule.scheduleAlarm` is invoked without awaiting success/failure. A late schedule can recreate an alarm after a later cancellation.

Serialize alarm operations and make every operation awaitable. Tests must cover schedule → cancel → schedule ordering and rejection.

**Finding N-02 — P1: cancel-all cannot see every native alarm**

Some cancellation helpers discover task IDs only from Expo scheduled notifications. Native alarms that have no Expo notification row can survive cancellation.

Maintain a deterministic native alarm ID per task and cancel by known task IDs, not only by scanning Expo notifications.

**Finding N-03 — P0: headless and foreground notification actions lack shared idempotency**

The foreground listener and headless handler can both observe a notification near app resume. The foreground has a short in-memory action dedupe window, but the headless path has no shared compare-and-set/version check.

Store an action/event ID or task revision durably and make COMPLETE/SKIP idempotent. EXTEND must reject a second application of the same event.

**Finding N-04 — P1: focus-session rows lack an active-session uniqueness rule**

Concurrent starts can create multiple active rows. Reads select the newest, while stop updates all active rows for the task.

Enforce one active session per task/app policy at the database boundary and make start/stop idempotent.

## 7. UI and state-model findings

**Finding U-01 — P1: whole-snapshot and single-row reducer actions are mixed**

`SET_TASKS`, `ADD_TASK`, `UPDATE_TASK`, and `DELETE_TASK` coexist. Schedule operations affect multiple rows, but future callers can accidentally use `UPDATE_TASK` for a command that should reconcile peers.

Prefer command results that provide the complete affected task set, then reconcile by ID in one reducer action.

**Finding U-02 — P1: task objects passed to modals can be stale**

Screens pass task objects captured during render but correctly route most actions by ID. The command layer must always reread the latest task by ID before mutation; it must never trust a modal’s copied task fields for durable writes.

**Finding U-03 — P2: notification action failures are not user-visible**

Notification handlers call `void completeTask`, `void extendTaskTime`, and `void skipTask`. Failures are logged but not surfaced or retried.

Show a recoverable in-app state on resume and record the failed event for retry.

**Finding U-04 — P2: custom reminder data is not honored**

`Task.reminders` exists in the model, but scheduling is largely hard-coded in `notificationService.ts`. Decide whether reminders are user-configurable task data or remove the unused contract.

## 8. Test audit

Current tests are strongest around pure helper functions and source-string contracts. They are not strong enough around the actual task command system.

### Existing strengths

- Basic task creation/status/query helpers.
- Basic conflict/rebalance/compression behavior.
- Notification ID/capacity/cancellation cases.
- Some background handler cases.
- Source-level ordering contracts for alarm/focus teardown.
- A unit test for the new operation queue’s ordering and failure recovery.

### Critical missing tests

#### P0 — must exist before broader task changes

1. AppContext command integration with a fake database and native side effects.
2. Refresh racing skip/complete/extend/delete.
3. Two rapid skips with React dispatch delayed.
4. Bulk skip/complete success and failure.
5. Bulk action retry after a failed save.
6. Foreground and headless duplicate notification delivery.
7. DB success followed by reminder/alarm/focus failure.
8. Active-task deletion with and without a focus-session PIN.
9. Local-time date lookup around UTC midnight.
10. Exact alarm schedule/cancel ordering.

#### P1 — scheduler and persistence matrix

1. Creation/edit conflict policy.
2. Contiguous slot compression after skip/complete/delete.
3. Gaps before and after skipped tasks.
4. Critical anchors and extension conflict handling.
5. Low/medium priority auto-skip branches.
6. Cross-midnight schedule health.
7. All task statuses in every query/helper.
8. SQLite round-trip, duplicate IDs, affected-row checks, and batch rollback.
9. Backup validation, duplicate import, merge, replace, and partial failure.
10. Native alarm failure and retry.

#### P2/device

1. Process death and foreground resume.
2. Notification action while killed.
3. OEM Doze/battery optimization behavior.
4. Reboot/alarm restoration.
5. Timezone change and DST.

## 9. Recommended repair order

Do not start with more UI patches. Repair the command/data boundary in this order.

### Phase 1 — Stop false task truth

1. Remove silent empty-array/default/recovery-DB behavior for task reads.
2. Add explicit DB availability to the schedule screen.
3. Keep the primary DB canonical.
4. Add AppContext integration tests for the current queue/snapshot containment.
5. Add durable task revision or compare-and-set semantics.

### Phase 2 — One task command layer

Create one command boundary for:

```text
createTask
editTask
resolveTasks(ids, status)
extendTask
deleteTask
refreshTaskSlice
```

Individual UI actions, bulk alerts, notification actions, backup/import, and headless handlers must call the same command semantics or a clearly documented headless adapter.

Every command should:

1. Validate current state and task revision.
2. Calculate the complete affected task set.
3. Commit the durable task mutation atomically.
4. Return the committed task result.
5. Reconcile notifications/native state idempotently.
6. Update React from the committed result, never from a copied input object.

### Phase 3 — Define schedule and status contracts

1. Decide exact start/end inclusivity.
2. Decide whether overdue is resolvable and schedule-blocking.
3. Define skip/complete/delete compression.
4. Define extension behavior around gaps and critical tasks.
5. Make conflict insertion/editing use the defined policy.
6. Add pure property/regression tests.

### Phase 4 — Unify native/headless reconciliation

1. Await and serialize native alarm operations.
2. Add durable notification-event idempotency.
3. Make headless actions revision-aware.
4. Make focus-session start/stop unique and idempotent.
5. Reconcile on resume without allowing stale reads to overwrite newer state.

### Phase 5 — Repair import/export and device lifecycle

1. Transactional backup validation/import.
2. Explicit task merge/replace semantics.
3. Background date helper shared with the UI.
4. Android process-death, reboot, alarm, timezone, and OEM tests.

## 10. Definition of “task system healthy”

The task system should not be considered repaired until all of these are true:

- A skipped task cannot return to unresolved/scheduled state because of a refresh or duplicate action.
- Bulk and individual resolution have the same durable and native side effects.
- A failed write never appears as a successful UI action.
- A failed read never appears as “no tasks.”
- A task mutation cannot silently overwrite a newer task revision.
- Headless and foreground actions are idempotent.
- Native alarms cannot outlive or recreate a resolved/deleted task.
- Deleting a focused task cannot leave an orphaned focus session or blocker.
- Creation/editing conflict behavior is explicit and tested.
- Status and time-boundary semantics are consistent across UI, DB, scheduler, notifications, and background jobs.
- Backup/import cannot partially destroy task data.
- Integration tests cover command ordering and failure injection.
- Device tests cover process death, notification actions, alarms, timezone changes, and OEM lifecycle behavior.

## 11. Validation note

Source-level validation completed for the current working tree:

- Changed task files have balanced delimiters.
- `git diff --check` passes.
- The task-operation queue has isolated ordering and rejection-recovery tests.
- Scheduler regression coverage includes the exact contiguous-slot compression boundary.

The FocusFlow TypeScript and Vitest commands could not run in this checkout because `node_modules` is not installed (`tsc` and `vitest` were unavailable). Android/device verification is also not available in this environment.
