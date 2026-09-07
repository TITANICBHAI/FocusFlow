---
name: FocusFlow features and fixes plan
description: Durable execution notes for the imported FocusFlow feature plan and its current audit boundaries.
---

The imported feature plan is stored in the FocusFlow artifact with a separate
mutable tracker. The tracker is the review surface: the original plan is not
treated as proof that a feature exists.

**Why:** The plan's `.focusflow` section describes missing pieces, but the
checkout already contains a partial incoming-link path that fails on Android
`content://` URIs and lacks the native `ContentResolver` bridge. Copying the
plan blindly would mark a broken path as complete and would miss the need for
an actual import-confirmation screen.

**How to apply:** Before implementing any plan section, compare it with the
current artifact and update the tracker. For `.focusflow`, keep Android native
source/config-plugin changes authoritative, read provider URIs through
`ContentResolver`, validate the backup envelope before restore, and do not put
full backup JSON in Expo Router params.

Focus-session teardown is intentionally DB-first and bounded: close the known
SQLite focus row before native stop calls, bound each native teardown bridge,
and persist the explicitly dismissed task ID so the 30-second auto-start check
cannot resurrect the same task occurrence. A user-started session clears that
dismissal; the existing intentional keep-until-end behavior remains valid.

**Why:** Native services can hang or survive a cold JS restart, while an open
SQLite row inflates daily totals and an auto-start retry can immediately restore
a session the user just stopped.

**How to apply:** Treat source-level lifecycle completion and Android/device
validation as separate gates. Reconciliation should clear orphaned sessions
immediately and resolved/expired sessions after the documented grace period.
