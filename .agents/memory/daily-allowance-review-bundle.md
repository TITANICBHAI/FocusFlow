---
name: Daily Allowance review bundle
description: The durable review and execution guidance for Daily Allowance bugs, risks, priorities, and traceability.
---

The Daily Allowance work is documented in the paired plain-English helper and technical review files under the FocusFlow artifact. The technical review and its status markers are the implementation source of truth; the plain-English guide explains user impact and expected behavior.

The confirmed fix set is intentionally narrow:

1. In the accessibility watchdog, skip exhaustion checks while `currentTimedPkg` matches the foreground package. This is necessary because count-mode recording increments usage before the session ends, making availability false during a valid active session.
2. In the foreground service, calculate interval-mode usage from `UsageEvents` inside the current allowance window. `queryUsageStats(INTERVAL_DAILY, ...)` is calendar-day scoped and can import usage from an earlier window.
3. Time-budget timing remains an investigation: the current code already skips FTS sync for fresh AccessibilityService signals and re-checks freshness before fallback expiry. A proposed extra scheduling-time guard is not a complete fix; reproduce stale-timer versus close/reopen handoff behavior before changing ownership.

Preserve the surrounding contracts while applying these fixes: all usage writes remain under `ALLOWANCE_USAGE_LOCK`, the Android Q event-type guard must match count reconciliation, interval mode must not gain the time-budget fallback timer, and count-mode apps remain excluded from foreground-session recovery.

**Why:** The review contains source-pinned findings and an explicit priority order, while the remaining time-budget symptom is not explained well enough to justify a timer rewrite or a new Bug 4.

**How to apply:** Read both documents before changing Daily Allowance. Keep the technical checklist status and source references current, use `[ ]` for unstarted work, `[x]` only after verification, and `[X]` only for blocked/deferred/not-applicable items with a note.