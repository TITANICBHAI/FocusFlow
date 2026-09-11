---
name: FocusFlow bug-fix bundle
description: The verified FocusFlow bug diagnosis and ordered implementation tracker imported with the project.
---

The FocusFlow artifact contains two user-provided source documents:

- `BUGFIX_DIAGNOSIS_1789086920049.md` is the verified technical diagnosis. It covers session-PIN handling and authorized native bypasses, settings and task lifecycle behavior, scheduling arbitration, and launcher UI/native issues.
- `BUGFIX_TRACKER_1789086920052.md` is the execution checklist. It establishes the dependency order: native authorized-bypass methods first, TypeScript call-site fixes second, then gating/cleanup, scheduling features, launcher fixes, and final cross-checks.

**Rule:** treat the diagnosis as the source of technical requirements and the tracker as the source of implementation order and status. Keep the tracker current after each completed prompt; do not mark a prompt complete without the corresponding verification.

**Why:** the user supplied these documents as the verified baseline for the next FocusFlow bug-fix work, and several later changes depend on the native session-ending bypass being available first.

**How to apply:** before implementing any listed bug, read the relevant diagnosis section and update the matching tracker rows in the FocusFlow artifact. Preserve the distinction between user-initiated PIN-protected stops and authorized system paths.