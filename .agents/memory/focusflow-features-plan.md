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
