---
name: FocusFlow persistence reliability plan
description: Authoritative staged plan for fixing blank-on-start persistence failures and migrating Android data ownership.
---

The detailed execution contract is stored at `artifacts/focusflow/PERSISTENCE_RELIABILITY_PLAN.md`, with the formal risk review in `artifacts/focusflow/PERSISTENCE_RELIABILITY_PLAN_REVIEW.md`. It keeps the React Native UI initially, moves canonical Android data to a native Room repository, retains only a small revisioned native enforcement snapshot, preserves today-only task queries, and includes cross-storage telemetry.

**Why:** The diagnostic logs show both a WAL checkpoint lock and an Expo/JSI constructor NPE. Treating both as dead handles and opening a recovery database obscures the root cause; dependent fallback operations then create misleading secondary errors. The project also uses generated native Android code and an old React Native bridge, so a half-cutover can leave real writers behind.

**How to apply:** Follow the amended order in the plan: baseline and inventory first, then observability, startup safety, a repository boundary over legacy storage, verified Room migration, native/background cutover, and only then cleanup. Require outbox reconciliation, generated-build verification, rollback policy, and a final direct-writer audit before declaring completion.