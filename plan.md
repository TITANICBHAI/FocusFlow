# Task Alarm and Notification Settings Plan

## Goal

Add task-alert controls to the existing Settings → Notifications section:

1. Let the user choose whether a task ending triggers the current ringing alarm experience or a notification-only alert. Keep ringing as the default, including for existing installations.
2. Let the user independently enable or disable each scheduled task reminder, in addition to the existing master **Enable Reminders** switch.

This plan is based on the current Android implementation. It does not cover unrelated notification families such as achievements, daily reflections, or analytics insights.

## Current implementation

- `AppSettings.taskRemindersEnabled` is the existing master switch and defaults to `true`.
- `ReminderPlanner` creates these scheduled task reminder slots:
  - 10, 5, and 1 minute before a task starts.
  - At task start.
  - 15-minute and 30-minute check-ins, when they fit within the task.
  - One minute before the task ends (“Start wrapping up!”).
- A separate live-task-status slot is maintained independently of the master reminder switch. Turning off task reminders must not remove or suppress this status behavior.
- `ReminderReceiver` reads the master setting when it handles a scheduled event, delivers due slots, refreshes the live status, and rearms the next event. Settings changes to the master switch request reconciliation.
- Task-end alarms are a separate path. `AlarmRepository` schedules them; both `TaskEndAlarmReceiver` and the foreground-service timer call `ForegroundTaskService.postTaskEndAlarmNotification`. That method posts a full-screen alarm notification, and `TaskAlarmActivity` starts the alarm ringtone and vibration.
- Task reminders use the shared `task-reminders` notification channel. The task-end alarm uses its own `task_alarm` channel.
- Settings preferences are represented in `AppSettings`, read and written by `SettingsRepository`, updated through `SettingsViewModel`, and included in portable backup settings through `PortableSettingsPolicy` and `BackupSettingsAdapter`.

## Proposed Settings behavior

Keep the existing **Enable Reminders** switch as the master switch. Under it, add an indented/grouped **Task reminders** section with individual switches:

- **Before task starts** — controls the existing 10-, 5-, and 1-minute pre-start reminders together.
- **At task start** — controls the reminder posted at the scheduled start time.
- **Mid-task check-ins** — controls both existing 15- and 30-minute check-ins together.
- **Wrapping up** — controls the existing one-minute-before-end reminder.

Keep the narrower grouping aligned to the reminders’ existing categories; do not add separate switches for every minute offset in this change. When the master switch is off, these individual preferences remain saved but no task reminder notifications are delivered. Re-enabling the master switch restores each category according to its saved preference. The live-task-status notification remains independent.

Add a distinct **Task end alert** choice in the same Notifications section:

- **Ringing alarm** — existing behavior: alarm sound/vibration and full-screen alarm experience.
- **Notification only** — post a normal, non-ringing task-end notification without launching the alarm activity or starting its ringtone. Tapping it should still let the user reach the task-end actions without starting the ringtone.

Default to **Ringing alarm**. Existing installs and backups without the new preference must resolve to that default.

## Implementation outline

1. **Settings model and persistence**
   - Add explicit preferences for the four reminder categories and the task-end alert mode.
   - Give new category switches defaults that preserve today’s behavior (enabled); default the alert mode to ringing.
   - Read/write the values through `SettingsRepository` and update them through `SettingsViewModel`.
   - Add the fields to portable backup export and import. Old backups that omit them must use the defaults above.
   - Preserve the current `taskRemindersEnabled` master-switch semantics.

2. **Reminder planning and delivery**
   - Extend reminder planning to accept the category preferences and omit disabled notification slots.
   - Keep `LIVE_STATUS_START` generation and live-status synchronization independent of reminder-category switches and the master switch.
   - Ensure the receiver uses current saved preferences for delivery and replanning.
   - Request reminder-chain reconciliation whenever the master or any category preference changes, so disabling a category does not leave its next alarm armed and enabling one takes effect without waiting for unrelated edits.
   - Preserve duplicate prevention, due-slot recovery, and the single-alarm-chain design.

3. **Task-end alert mode**
   - Apply the saved mode consistently to both task-end entry points: `TaskEndAlarmReceiver` and the foreground-service timer.
   - In ringing mode, retain the current task alarm channel, full-screen behavior, ringtone, vibration, deduplication, and task actions.
   - In notification-only mode, post a non-ringing notification and do not automatically launch `TaskAlarmActivity`. Ensure tapping the notification does not start the alarm ringtone; route to a non-ringing task-end action flow or make the existing activity explicitly suppress alarm playback for this mode.
   - Keep task-end alarm scheduling, validation, reconciliation, and end-of-task processing active in either mode. This choice changes delivery style, not whether task completion is detected.
   - Use a distinct silent notification channel for notification-only task-end alerts if required by Android’s channel behavior. Do not try to change the sound of an already-created channel in place; Android retains channel settings and user overrides.
   - Do not conflate this preference with Android exact-alarm permission or notification permission.

4. **Settings UI**
   - Place the grouped controls under the existing Settings → Notifications section.
   - Keep all new category toggles visible but subordinate to the existing master switch; disable their interaction while the master is off without clearing their saved values.
   - Present the task-end choice as a two-option control with clear wording that distinguishes ringing from notification-only.
   - Retain the existing notification-permission action and daily-reflection control.

## Verification plan

- Unit-test reminder planning for each category independently, master reminders off, combinations of enabled categories, and the requirement that live-status slots remain available.
- Test defaults and persistence for a fresh settings store, existing preferences with no new keys, updates through `SettingsViewModel`, and portable backup export/import with both old and new backup data.
- Test that settings changes replan the reminder chain and that due-slot delivery respects the latest saved category values.
- Test that both task-end entry points select the same mode. In notification-only mode, verify no full-screen intent or ringtone-starting activity is launched; in ringing mode, verify the existing path is unchanged.
- Run the Android unit-test workflow and inspect the changed settings and delivery paths. Device verification is required for sound/channel behavior and full-screen behavior, since Android notification-channel state and OEM behavior cannot be fully verified by JVM tests.
- Include a device acceptance check that schedules a task end, verifies ringing by default, switches to notification-only, and confirms a later task end produces a quiet notification whose task-end actions remain usable.

## Scope boundaries and risks

- The individual mid-task switch groups the current 15- and 30-minute reminders; it does not create a new “halfway” calculation.
- Reminder category switches suppress or allow FocusFlow reminder notifications; Android’s app-level permission and per-channel user settings can still prevent or alter their presentation.
- This work must not disable the end-time alarm scheduler, live-task-status notification, or task-end processing when task reminders are disabled.
- A notification-only task-end alert must not accidentally open an activity that starts ringing. The existing `TaskAlarmActivity` starts its ringtone on launch, so the notification-only tap route needs explicit handling.
- The current task-end channel is created once and cannot be reliably converted between audible and silent behavior by editing its configuration later. Keep the ringing channel intact and isolate notification-only delivery from it.

## Working interpretation

“Halfway,” “wrapping up,” and “starting” refer to the existing mid-task, almost-done, and pre-start/at-start reminder families. This plan groups the existing 15- and 30-minute check-ins into one Mid-task switch and the 10/5/1-minute warnings into one Before-start switch, with separate At-start and Wrapping-up switches.

## Implementation status

The code changes are in place. The JVM unit-test workflow passed for both app variants, and Android test sources compile for both variants. Device acceptance remains necessary to confirm notification-channel sound/vibration and full-screen behavior on a real Android device.
