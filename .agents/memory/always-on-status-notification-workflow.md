---
name: Always-on status notification workflow
description: Working context and progress-recording rules for the FocusFlow Android notification implementation.
---

Keep the service running whenever existing background consent is granted; do not add app-side idle/active gating or an in-app off switch in this iteration. An idle-only opt-out requires a later decision. Keep the scheduled-task card separate. The health state is useful but nonessential, and the allowance refactor is deferred.

**Why:** The user chose always-on behavior to avoid bugs caused by app-side idle/active decisions.

**How to apply:** Preserve these scope decisions when changing the foreground service or its notifications. Re-check current source and tests rather than relying on old implementation-plan or tracker references.

Unknown enforcement health is a checking state, not a failure. Keep the idle notification neutral until a completed check confirms a missing required permission; only confirmed missing permissions should show “Needs attention” and open Permissions.

**Why:** The foreground service must post its notification before the asynchronous permission check completes, and treating unknown as failure creates a false warning on startup.

**How to apply:** Keep `isKnown` distinct from `needsAttention` when building idle status cards. Unknown health should show a checking message and use the Home tap target.

Notifications may remain optional for core blocking, but the permission is required for status-notification health because app-level notification disablement hides the foreground card from the notification shade.

**Why:** A healthy enforcement state alone does not guarantee users can see the always-on service status.

**How to apply:** Include notification visibility in the foreground status health check without reclassifying it as mandatory for all blocking features.
