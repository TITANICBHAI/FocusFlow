---
name: Task operation ordering
description: Task UI reads and mutations must share one ordering boundary.
---

Task actions and task refreshes must be serialized together at the AppContext boundary. A database write queue by itself is insufficient because a refresh can read before a mutation commits and dispatch that stale result afterward.

**Why:** This race made skipped tasks reappear as unresolved or caused bulk actions to reconcile against an older in-memory task snapshot.

**How to apply:** Route task refresh, bulk resolution, and task mutations through the same per-provider queue; resolve bulk actions from the latest task snapshot and cancel reminders for every terminal task.