# FocusFlow Protected Surfaces — Do Not Touch or Duplicate

This is a hard guardrail for every agent, contributor, and future implementation
pass, regardless of which plan, task, or workstream they are following.

## Absolute no-touch areas

Do **not** modify, replace, refactor, redesign, remove, or duplicate:

- Standalone block behavior or any of its related state, enforcement, UI, or
  cleanup paths.
- Always-On behavior or any of its related state, enforcement, UI, lifecycle,
  or recovery paths.
- The Accessibility window-overlay behavior, including every related helper,
  lifecycle path, dismissal path, and visual/interaction contract. This area is
  protected and must not be touched by any means.
- The existing back/back/home/back action sequence and the logic that preserves
  that sequence.
- The Kotlin block-overlay implementation, especially
  `android-native/app/src/main/java/com/tbtechs/focusflow/services/BlockOverlayActivity.kt`
  and its directly related overlay code.

Do not create a second implementation under a new name, move these behaviors
into a parallel abstraction, or “clean up” the existing code while working on a
nearby feature. A new plan or imported review does not override this rule.

## Required behavior for nearby work

When a change touches a neighboring service, preference, receiver, VPN path,
navigation path, persistence path, or recovery path:

1. Preserve the existing standalone, Always-On, accessibility-window-overlay,
   back/back/home/back, and Kotlin block-overlay contracts.
2. Keep independent enforcement sources independent; do not silently merge
   their state or lifecycle.
3. Add or update tests around the new behavior without copying protected
   implementation into a second test or production path.
4. Stop and ask for explicit scope clarification if the requested change would
   require editing a protected surface.

This note is intentionally stronger than ordinary plan guidance: the protected
areas are regression-sensitive product behavior, not unfinished placeholders.