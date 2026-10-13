---
name: Legacy 24:00 schedule ends
description: Compatibility rule for manually stored recurring schedules that end at 24:00.
---

Treat a raw stored schedule endpoint of 24:00 as midnight at the start of the following day. Keep user-facing schedule controls and backup-import validation limited to hours 0–23.

**Why:** 24:00 is a valid exclusive endpoint for manually edited persisted schedules; preserving it avoids breaking existing data without expanding accepted UI or backup values.

**How to apply:** When changing shared schedule validation or boundary calculation, accept 1440 only as the end minute and resolve its boundary to the next day's midnight.
