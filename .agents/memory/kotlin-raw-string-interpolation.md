---
name: Kotlin raw-string interpolation
description: Preserve literal dollar signs in Kotlin triple-quoted prompt strings.
---

Kotlin triple-quoted strings do not process backslash escapes, but they still process `$` string templates. A regex end anchor `$` next to a Markdown backtick can be parsed as a template opener; use `${'$'}` when the output must contain a literal dollar sign.

**Why:** A remote Kotlin compile treated a prompt regex anchor adjacent to a Markdown delimiter as an unresolved template reference; escaping the dollar preserved the intended regex.

**How to apply:** When editing Kotlin raw strings containing regexes, shell syntax, or other dollar signs, inspect interpolation boundaries and verify with compilation instead of assuming raw strings disable templates.
