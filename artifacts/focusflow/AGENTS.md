# FocusFlow Agent Instructions

## Read this before doing FocusFlow work

This file is a mandatory entry point for every agent working in this artifact, regardless of the model or tool being used. Before inspecting implementation details, changing code, updating a plan, or making an architectural recommendation:

1. Read [`ARCHITECTURE.md`](./ARCHITECTURE.md) completely.
2. Identify the relevant execution plan/tracker in this directory.
3. Read the relevant plan before editing.
4. Re-check the current source before applying any line-specific recommendation from a plan.

Do not skip this because a task appears small. FocusFlow's enforcement behavior runs across Android services, receivers, persistence, permissions, background work, widgets, and UI. A local-looking change can break behavior after process death, reboot, screen-off, service loss, or a migration.

## Non-negotiable target direction

FocusFlow mobile is moving from the current React Native/Expo + Kotlin hybrid to a **pure Kotlin Android application using Jetpack Compose**:

- No JavaScript or TypeScript runtime in the target app.
- No React Native UI, Expo Router, or new React Native bridge modules in the target app.
- No new behavior should be designed around the JS/native bridge.
- Use Kotlin repositories, Room where specified, the synchronous enforcement preference contract, ViewModels/StateFlow, WorkManager, and Compose navigation as described by `ARCHITECTURE.md`.
- Existing Kotlin enforcement services are valuable migration sources. Preserve their behavior first; do not rewrite load-bearing enforcement logic as a cleanup exercise.
- Treat the current Expo/TypeScript app as the legacy behavior and migration source, not as the destination architecture.

The architecture document's pure-Kotlin target takes precedence over older hybrid-era assumptions in `replit.md`, `KOTLIN_MIGRATION.md`, `FINAL_PLAN.md`, feature plans, persistence plans, bug-fix plans, and tracker files when they disagree. Those documents still contain useful implementation facts and current-checkout status; use them in the scope they describe and record any decision that changes the target architecture.

## Required preservation rules

- Preserve blocking, allowance, VPN, alarm, boot-recovery, widget, backup/import, PIN, permission, and accessibility disclosure contracts unless the architecture explicitly changes them.
- Keep the two existing allowance implementations intact during the first migration pass. Consolidation is a deliberate later decision, not an incidental refactor.
- Keep enforcement services on synchronous `SharedPreferences` reads in their hot paths. Do not create a second source of truth that can diverge from those services.
- Preserve exact-alarm fallback behavior, Android 14 foreground-service declarations, boot clock-tamper protection, VPN policy-generation ordering, active widget pushes, and legacy backup compatibility.
- Keep Android automatic backup disabled. FocusFlow data moves through explicit export/import.
- Keep source-level implementation evidence separate from generated Android, Gradle, emulator, and physical-device validation. Never mark device behavior validated based only on TypeScript tests or source inspection.

## Working protocol

- Read first, then edit the smallest relevant surface.
- Do not install dependencies or create a generated Android project merely to read these documents.
- When implementing the Kotlin migration, follow the execution order and risk register in `ARCHITECTURE.md`.
- When changing an architectural decision, update `ARCHITECTURE.md` and the relevant tracker in the same change.
- Do not duplicate the full architecture in another plan. Link to this document instead.