---
name: FocusFlow stats and insights plan
description: Durable pointer for the on-device Stats & Insights redesign and its implementation tracker.
---

The Stats & Insights redesign plan is stored in the FocusFlow artifact with a
separate mutable tracker. The plan is the product and architecture reference;
the tracker is the review surface and must be updated as implementation proceeds.

**Why:** The redesign depends on local-only analytics, deterministic rule-based
insight templates, a strict UsageStats permission gate for the 3-month view, and
separate source/device validation. Keeping the original plan immutable prevents
implementation status from being confused with the intended design.

**How to apply:** Before implementing a section, compare it with the current
FocusFlow code and update the tracker when the plan does not match reality.
Keep analytics on-device, use existing data before proposing schema changes,
preserve the permission gate, and distinguish source-level completion from
Android/device verification. Update the tracker log after each implementation
slice.