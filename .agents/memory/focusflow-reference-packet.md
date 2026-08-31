---
name: FocusFlow reference packet
description: Imported FocusFlow plans, reviews, tracking notes, and implementation guidance stored with the mobile artifact.
---

The imported FocusFlow reference packet is kept in `artifacts/focusflow/`.
It includes accessibility recovery, database logging, navigation feedback,
daily allowance, persistence/migration/reliability, Gradle release, VPN
enforcement, recommendations, and test-plan documents. The packet contains
both implementation guidance and reviews that may describe work already
present, so it is reference material rather than permission to change code.

Use the current checkout to verify every status and finding before editing.
Read the protected-surfaces guardrail first; it overrides nearby plan language
when a plan suggests touching standalone, Always-On, accessibility-window
overlay, back/back/home/back, or Kotlin block-overlay behavior.

**Why:** The packet consolidates several independent reviews and tracking
documents, including newer revisions of existing FocusFlow plans. Treating
every unchecked item as a fresh requirement would cause duplicate work or
regressions.

**How to apply:** Start with
`artifacts/focusflow/FOCUSFLOW_PROTECTED_SURFACES.md`, then consult the
specific packet document for the requested area and validate it against the
current source before making a change.