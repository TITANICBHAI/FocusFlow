---
name: Daily allowance visual states
description: The reference-driven presentation and interaction model for the Daily Allowance editor.
---

The Daily Allowance editor uses a flat full-width app list. Active rows show an amber allowance rail, a compact summary, sun action, and expand/collapse affordance; the selected mode controls render inline beneath the row.

**Why:** The supplied references distinguish collapsed active apps from expanded Count, Time Budget, and Interval states through hierarchy and inline controls rather than separate cards or screens.

**How to apply:** Preserve the three persisted modes and their existing save semantics when changing this screen. Keep inactive-app tap-to-add, active-app tap-to-expand, and long-press removal behavior aligned with the reference interaction hints.