---
name: Daily Allowance review bundle
description: The durable review and execution guidance for Daily Allowance bugs, risks, priorities, and traceability.
---

The Daily Allowance work is documented in the paired plain-English helper and technical review files under the FocusFlow artifact. The technical review and its status markers are the implementation source of truth; the plain-English guide explains user impact and expected behavior.

The confirmed fix set is intentionally narrow:

1. In the accessibility watchdog, skip exhaustion checks while `currentTimedPkg` matches the foreground package. This is necessary because count-mode recording increments usage before the session ends, making availability false during a valid active session.
2. In the foreground service, calculate interval-mode usage from `UsageEvents` inside the current allowance window. `queryUsageStats(INTERVAL_DAILY, ...)` is calendar-day scoped and can import usage from an earlier window.
3. For time-budget expiry, arm the foreground-service timer only when the accessibility session signal is absent or stale. A fresh signal means the accessibility service's live timer owns enforcement.

Preserve the surrounding contracts while applying these fixes: all usage writes remain under `ALLOWANCE_USAGE_LOCK`, the Android Q event-type guard must match count reconciliation, interval mode must not gain the time-budget fallback timer, and count-mode apps remain excluded from foreground-session recovery.

**Why:** The review contains source-pinned findings and an explicit priority order, so future work should not rediscover or reinterpret the same allowance behavior.

**How to apply:** Read both documents before changing Daily Allowance. Keep the technical checklist status and source references current, use `[ ]` for unstarted work, `[x]` only after verification, and `[X]` only for blocked/deferred/not-applicable items with a note.