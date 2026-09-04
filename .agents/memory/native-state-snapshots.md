---
name: Native state snapshots
description: Reliability rule for critical FocusFlow SharedPreferences transitions.
---

Critical focus and standalone enforcement transitions must publish the complete native state through one synchronous `SharedPreferences` commit, and a failed commit must propagate to the caller. Optional or cosmetic preference writes may remain best-effort.

**Why:** Native enforcement readers can run independently of the JavaScript bundle, so separate writes can expose a partial state; silently swallowing a failed critical commit makes the UI and enforcement layers disagree.

**How to apply:** Use the atomic snapshot bridge for session start/stop and standalone block transitions. Keep VPN implementation changes out of this persistence work unless a separate VPN task explicitly requires them.