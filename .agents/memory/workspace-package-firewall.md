---
name: Workspace package firewall
description: Environment-specific limitation encountered while installing the FocusFlow workspace dependencies.
---

A full frozen pnpm install can be blocked by the package firewall on an unrelated workspace tarball such as `orval@8.8.1`; a filtered FocusFlow install can still link successfully.

**Why:** The failure prevents the workspace `tsc` binary and React Native/Expo type packages from becoming available, so a typecheck may show broad missing-module and standard-library errors that do not identify application-code defects.

**How to apply:** Preserve the existing package manifest and lockfile when setup is not the requested work. If installation is authorized, try the narrowest workspace filter first, verify both manifest and lockfile, and do not mark a phase typecheck as passing unless the normal workspace command completes successfully.