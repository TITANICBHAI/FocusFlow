---
name: Kotlin validation toolchain
description: The FocusFlow Kotlin module cannot be compiled in the current workspace shell without an installed JDK.
---

The current workspace shell has no `java`, `JAVA_HOME`, or `kotlinc`, so Gradle/Kotlin verification stops before dependency resolution.

**Why:** A source-level check can catch whitespace and obvious contract errors, but it cannot validate Compose APIs or Kotlin type resolution.

**How to apply:** Before claiming Kotlin build validation, check for `java` and `JAVA_HOME`; if absent, report the limitation and complete static checks instead.