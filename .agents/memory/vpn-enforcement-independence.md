---
name: VPN enforcement independence
description: Durable separation between FocusFlow VPN network blocking and ordinary overlay/accessibility blocking, including the planned opt-in focus mirror.
---

VPN protection is an independent enforcement layer. Android per-app VPN routing operates at the package UID level, so a package registered in the target set loses both foreground and background network access; the VPN itself should not detect foreground/background state. A package may be selected for VPN blocking without being selected for an overlay block, and a configured VPN package list must activate and remain health-monitored even when no ordinary focus or standalone block session is active.

For the proposed background-enforcement feature, keep explicit VPN selections separate from Accessibility focus rules and add focus-disallowed packages only through an opt-in native policy coordinator. Use `PER_APP` mode, stop on an empty target set, preserve visible permission/conflict/failure states, and do not copy implementation code from the GPL-3.0 Silent Guardian reference.

**Why:** The product requirement is to block network traffic for selected apps without making AccessibilityService detection a prerequisite; coupling the two causes VPN-only selections to be inert. The optional mirror must not unexpectedly convert every focus-blocked foreground rule into a background network block.

**How to apply:** Treat the VPN target set as native durable policy, merge supported sources, reconfigure only when the effective set changes, restore it after lifecycle events, and preserve the AccessibilityService overlay pipeline separately. Use Silent Guardian only for independent architectural comparison; its GPL license makes source copying unsafe without a deliberate legal review.