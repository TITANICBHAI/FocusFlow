---
name: FocusFlow protected surfaces
description: Non-negotiable boundaries for FocusFlow work around blocking, overlays, navigation, and Kotlin enforcement.
---

Every FocusFlow agent must treat these as protected surfaces: standalone block
behavior and all related paths; Always-On behavior and all related paths; the
Accessibility window-overlay behavior and its related lifecycle/dismissal/UI
paths; the existing back/back/home/back action sequence; and the Kotlin block
overlay implementation (`BlockOverlayActivity.kt` and directly related code).

Do not touch, replace, refactor, redesign, remove, or duplicate them. Do not
create parallel implementations under another name. If nearby work appears to
require a protected edit, stop and request explicit scope clarification rather
than making the change implicitly.

**Why:** These surfaces are regression-sensitive enforcement and navigation
contracts. Duplicating or “cleaning up” them can split state, change blocking
semantics, or break the deliberate recovery/action sequence.

**How to apply:** Read `artifacts/focusflow/FOCUSFLOW_PROTECTED_SURFACES.md`
before any FocusFlow change. Preserve these contracts when changing adjacent
VPN, persistence, receiver, lifecycle, accessibility, or navigation code, and
test new behavior without copying protected production logic.