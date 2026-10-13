---
name: React sizing reference
description: The dimensional baseline for keeping the Kotlin Compose UI compact and consistent with the React product.
---

Use the React implementation's design tokens and component measurements as the source of truth for Kotlin Compose sizing. Prefer compact values around 11/13/15/18/22sp typography, 6/10/16dp radii, and 4/8/12/16/24/32dp spacing unless interaction requirements justify more.

**Why:** Screenshot-based estimates led to oversized Kotlin headers, FABs, task cards, switches, navigation items, and task-editor controls. The React source provides the actual product scale.

**How to apply:** Before changing a shared or reference-driven UI component, compare its dimensions with the React baseline. Preserve business behavior and content while shrinking presentation geometry toward the reference values.

Main-tab follow-through uses a single shared 44×26dp switch across Settings, Defense, task editing, and protection controls. Stats uses explicit flat cards with 16dp normal radii, 24dp hero radius, 12dp normal padding, and compact custom period pills rather than fixed-width segmented buttons.

**Why:** Independent Material controls and elevation caused the Kotlin screens to drift into an oversized Android-settings/dashboard appearance even when their logic matched the React product.

**How to apply:** Reuse the shared switch and explicit card wrappers for new protection or analytics rows. Keep large typography only for the primary KPI or timer, not surrounding chrome.