---
name: FocusFlow launcher two-theme system
description: The current launcher authority is the Classic + Glassy two-theme plan with a separate mutable tracker.
---

The authoritative launcher plan is preserved unchanged at
`artifacts/focusflow/LAUNCHER_TWO_THEME_PLAN_1788803550916.md`.

The mutable implementation tracker is at
`artifacts/focusflow/LAUNCHER_TWO_THEME_PLAN_TRACKING.md`.

The Glassy visual references and their plan mapping are at
`artifacts/focusflow/GLASSY_LAUNCHER_VISUAL_REVIEW.md`.

The Classic visual references and their plan mapping are at
`artifacts/focusflow/CLASSIC_LAUNCHER_VISUAL_REVIEW.md`.

**Rule:** Future agents working on the launcher must read the authoritative plan
before implementation and update the tracker after implementation or validation
progress. Do not edit the authoritative plan to record status.

**Why:** The newer plan explicitly supersedes the earlier launcher redesign and
defines two complete visual modes, shared data/filtering behavior, and the
Classic/Glassy drawer differences. A separate tracker keeps that source brief
auditable while allowing implementation state to change.

**How to apply:** Treat the plan as the source of requirements, use the tracker
for status and validation notes, use the visual reviews only to understand the
intended Classic and Glassy presentations, and preserve the existing native/RN
file boundaries described in the plan.