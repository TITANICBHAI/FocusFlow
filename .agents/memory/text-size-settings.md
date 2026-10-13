---
name: Text size settings
description: Durable product constraints for FocusFlow's text scaling feature.
---

The text-size feature uses a General scale and nullable per-tab replacement
scales for Schedule, Focus, Stats, Settings, and Defense. A null override
inherits General; a non-null override replaces it rather than stacking with it.

Show tab groups in bottom-navigation order: Focus, Schedule, Defense, Stats, and
Settings, using each tab's matching navigation icon. Keep Stats in Text Size,
but do not list it in Settings > About.

**Why:** The user requested that Text Size mirror the navigation order and icons
and that Stats be removed from the About section.

**How to apply:** Keep the tab order and icon mapping aligned with the main
bottom navigation; removing the About shortcut must not remove Stats itself or
its text-size controls.

**Why:** The user superseded the earlier Home/Schedule 100% pin and asked for
Schedule to receive the same independent override as the other tabs.

**How to apply:** Keep the bottom navigation scaffold outside each tab's scale
provider. Put controls on the separate Text Size screen, grouped in expandable
tab accordions rather than a flat list. Audit before adding screen-level
customization; plain Android views such as the block overlay need direct
preference reads instead of Compose locals.

The feature has two distinct implementation shapes: interconnected model,
persistence, theme, navigation, settings UI, and overlay wiring; plus a
separate mechanical conversion of eligible Compose text-size literals. Existing
defaults must remain visually unchanged, and non-composable `.sp` usages require
manual review rather than blind replacement.

**Why:** Mixing the two jobs makes scope errors likely, especially around Home,
shared components, and top-level typography.

**How to apply:** Use the saved work handoff documents as the agent pre-read and
report all skipped files or source mismatches explicitly.