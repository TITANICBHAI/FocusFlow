---
name: Gradle test-filter verification
description: Avoid false confidence from targeted Gradle test filters that match no test class.
---

When running a focused Gradle test command with `--tests`, confirm the generated XML reports include every expected fully qualified test class, not just a successful Gradle exit code.

**Why:** A filter using the wrong package/class name was silently ignored while the remaining filtered tests passed, so the missing class was not exercised.

**How to apply:** After a filtered JVM run, inspect `app/build/test-results/testDebugUnitTest/TEST-*.xml` for expected class names and aggregate test counts before reporting coverage.
