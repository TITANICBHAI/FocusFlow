---
name: Patch application failures
description: How to handle a failed multi-hunk workspace patch.
---

A multi-hunk patch that reports failure may still have applied earlier hunks to the workspace.

**Why:** A later context mismatch occurred after previous hunks had already changed the file, so retrying the whole patch could duplicate or conflict with those changes.

**How to apply:** After any patch failure, reread or inspect the affected file and current diff before preparing a retry; do not assume the operation was atomic.
