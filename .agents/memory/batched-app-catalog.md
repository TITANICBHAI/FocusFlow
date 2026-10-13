---
name: Batched installed-app catalog
description: The shared installed-app loader publishes metadata in batches while feature screens retain independent selection and configuration state.
---

The installed-app catalog is shared discovery data only. VPN, Always-On, Daily Allowance, and Allowed During Focus must continue owning their own selected packages, drafts, presets, and saved configuration.

**Why:** Batching reduces repeated list copying and Compose recompositions without coupling unrelated feature behavior or persistence.

**How to apply:** Change the catalog loader or batch size independently from feature selection logic; keep feature-specific filtering and mutations at the screen boundary.

Screens that return from Android system app flows, such as launcher settings or uninstall confirmation, should refresh the shared catalog on resume rather than trust the time-limited cache.

**Why:** The cached package list can be stale after an app is installed or removed while the system flow is open.

**How to apply:** Trigger an explicit refresh through the shared loader when that screen resumes; do not add a second per-screen app catalog.