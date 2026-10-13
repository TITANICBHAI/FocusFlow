---
name: Coroutine test dispatchers
description: Deterministic ViewModel tests that cross repository I/O and Main dispatchers.
---

When a `runTest` test drives a ViewModel whose repository switches to real `Dispatchers.IO`, `advanceUntilIdle()` can return while the I/O continuation is still pending. Resetting Main at that point can produce stale-state assertions or a late attempt to resume through Android's unavailable Main dispatcher. Inject a repository I/O dispatcher backed by the same test scheduler. If the test is not about one-time migrations that launch their own non-injected I/O work, mark those migrations complete in the test preferences.

**Why:** External I/O is not controlled by the test scheduler, so the test can finish before the ViewModel update has completed.

**How to apply:** Preserve `Dispatchers.IO` as the production default; use the shared test scheduler for repository work in ViewModel wiring tests.
