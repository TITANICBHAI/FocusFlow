---
name: Artifact preview dependency installation
description: Multi-artifact preview servers need dependencies installed in the artifact directory when the root project has a separate package graph.
---

Install dependencies for a generated artifact from its own directory when its preview workflow uses that artifact's package.json. A root-level install can fail on unrelated workspace peer conflicts and still leave the artifact unable to build.

**Why:** The Kotlin app root and the FocusFlow redesign artifact use separate JavaScript toolchains; the root graph was not a valid substitute for the artifact graph.

**How to apply:** For artifact preview build failures caused by missing frontend packages, prefer an artifact-local package install, then run the artifact's build and typecheck before restarting its managed workflow.