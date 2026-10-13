---
name: Force-stop alarm recovery
description: User-observed task-end alarms stop firing after Android force-stop and must be re-armed after relaunch.
---

The user observed that task-end alarms stopped firing after force-stopping FocusFlow. Successful alarm restoration after manually reopening the app is a required device acceptance scenario.

**Why:** JVM unit tests cannot reproduce Android force-stop state or validate real `AlarmManager` scheduling, and the user reported complete loss of alarms after force-stop.

**How to apply:** After changing task-alarm reconciliation, verify on an Android device: schedule a future task, force-stop the app, reopen it, and confirm the alarm is re-armed without a reconciliation diagnostic.
