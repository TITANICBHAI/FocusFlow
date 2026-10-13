---
name: Kotlin Compose scope audits
description: Avoid false positives when checking whether Compose-only calls are inside a composable function.
---

When auditing Kotlin UI call sites, do not treat an expression-bodied local function as extending to the next function declaration or the end of its enclosing composable. Its scope ends with its expression; model that boundary before classifying later call sites.

**Why:** A simple lexical scan temporarily classified text calls after a one-line Boolean helper as non-composable even though they remained inside the enclosing `@Composable` function.

**How to apply:** For source-level checks of `@Composable` extensions or calls, account for both block-bodied and expression-bodied functions, then inspect any flagged site in its actual enclosing declaration.