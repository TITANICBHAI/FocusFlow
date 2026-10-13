---
name: VPN batch test deferral
description: User instruction for when to run tests during the FocusFlow VPN wiring batches.
---

For the FocusFlow VPN wiring batches, do not run tests during intermediate batches, even if a later batch checklist asks for them. Defer all test execution until the designated final verification, and review the git diff after each batch.

**Why:** the user asked.

**How to apply:** While working through batches documented in `fixes/VPN_WIRING_FIX_TRACKER.md`, add the planned tests but do not execute them until the final verification step.
