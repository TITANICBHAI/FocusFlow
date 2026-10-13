---
name: Stats fix plan
description: Durable correctness decisions for FocusFlow Stats findings and analytics.
---

Finding resolution is based on 21 calendar days without positive evidence for
the exact detection type and subject package. Store the last positive evidence
time; do not count background-job runs because Android may skip executions.
Multi-candidate detectors must evaluate all qualifying candidates so a
non-winning candidate is not mistaken for an absent one.

**Why:** A run counter undercounts elapsed quiet time, and winner-only detector
results do not prove that other qualifying candidates disappeared.

**How to apply:** Update evidence timestamps on every positive detection,
resolve only after the elapsed-time threshold, account for cold-start history,
and share candidate evaluation logic between winner submission and absence
checks.

Intentional findings remain suppressed through their 60-day deadline, and the
weekly creation cooldown is based on the latest finding across all states, not
only unread findings.

**Why:** Fingerprint drift must not bypass an explicit user choice, and using
only unread findings lets an engaged user receive a new finding every day.

**How to apply:** Check suppression time before fingerprint comparison and query
the latest creation timestamp without a state filter.

Three-month hourly usage must use persisted genuine per-hour history and should
not be computed from aggregate UsageStats buckets. That expensive hourly read
should run only for the three-month window.

**Why:** OS aggregate buckets do not retain an hour-of-day dimension, so
assigning a whole bucket to its start hour produces structurally false charts.

**How to apply:** Sum stored daily hourly arrays across the requested range and
gate the read by the active analytics window.

Event-history coverage is per local date, not all-or-nothing for a multi-day
range. If retention truncates the first requested date, later dates after the
earliest retained event can still be complete; omit only dates without full
coverage.

**Why:** A partially covered range initially looked wholly incomplete even
though later full dates had valid coverage.

**How to apply:** Evaluate COMPLETE/PARTIAL coverage independently for each
local day when merging event-backed detector history with persisted rollups.

Device-usage aggregation must preserve every per-app row through date selection;
never reduce a list of app-day rows to one row per date.

**Why:** Several apps can have usage on the same date. Keeping only one row can
discard the rest, and the row retained may be below the UI's display threshold,
making real device use appear to be zero.

**How to apply:** Group rows by date into lists, carry all rows into the selected
history source, and cover multi-app dates with a regression test.