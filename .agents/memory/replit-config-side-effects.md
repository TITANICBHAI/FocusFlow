---
name: Replit config side effects
description: Automatic toolchain provisioning and protected .replit edits in this workspace
---

Invoking a language runtime that is not already configured can automatically add its module to `.replit`. Direct edits to `.replit` are blocked by the workspace; restore or change it by writing the full TOML to a temporary file inside the workspace and calling `verifyAndReplaceDotReplit({ tempFilePath: absolutePath })`. Relative paths fail with `DOT_REPLIT_EDITING_ERROR`.

**Why:** A Python source-check command caused an unrelated module entry to appear, and the normal patch operation was rejected by the config guard.

**How to apply:** Prefer already-configured shell or Node tools for one-off checks. If a `.replit` change is needed, use the validated temporary-file replacement flow and verify the final diff.

The validated replacement helper may remove its temporary input file; check before trying to clean it up manually.

**Why:** After a successful replacement, the prepared temporary file was already absent.

**How to apply:** Verify the `.replit` diff after replacement. Treat a missing temporary file afterward as normal rather than as a failed restore.

A workflow restart can also normalize `.replit` and remove an unrelated `[[ports]]` block. Finish workflow runs before restoring the desired TOML.

**Why:** Restoring the original port mapping succeeded, but restarting the Android unit-test workflow removed that block again.

**How to apply:** After the final workflow restart, restore the full desired `.replit` content through the validated replacement flow and verify the diff; do not restart a workflow afterward unless prepared to restore it again.

On 2026-10-08, after a bootstrap-backed Android test run and a Python command summarizing test XML, `.replit`'s timestamp changed and a pre-existing dirty status disappeared. Neither test script references that file; the precise trigger is unclear, though runtime startup may normalize workspace config.

**Why:** A config change visible before verification was no longer present afterward, so assuming it had been preserved or intentionally discarded would be unsafe.

**How to apply:** Compare `.replit` before and after bootstrap-backed tests and runtime-invoking commands. If a prior diff disappears, do not reconstruct it from memory; inspect the final file and ask the owner before restoring unknown configuration.