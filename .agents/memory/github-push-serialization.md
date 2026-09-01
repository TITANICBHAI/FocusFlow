---
name: GitHub push serialization
description: Same-branch GitHub API push workflows can race when run concurrently.
---

Push workflows targeting the same GitHub branch must be run serially, or a retry must reacquire the branch ref after another push completes.

**Why:** Both FocusFlow push workflows can read the same base ref and then one is rejected with GitHub's “Update is not a fast forward” response after the other updates the branch.

**How to apply:** When multiple push workflows target the same repository and branch, let one finish before starting the next; if a workflow loses the race, rerun only that failed workflow against the latest ref.