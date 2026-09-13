# FocusFlow Android App

## Start here

FocusFlow mobile is moving from the current Expo/React Native + Kotlin hybrid to a **pure Kotlin + Jetpack Compose Android application**.

Before any agent or developer changes this artifact:

1. Read [`AGENTS.md`](AGENTS.md).
2. Read [`ARCHITECTURE.md`](ARCHITECTURE.md) completely.
3. Read the relevant execution plan or tracker before editing.

## Architecture direction

- The pure Kotlin/Jetpack Compose design in [`ARCHITECTURE.md`](ARCHITECTURE.md) is the target source of truth.
- The current Expo/TypeScript files are a legacy behavior and migration reference, not the destination architecture.
- Do not add new JavaScript, TypeScript, React Native, Expo UI, or React Native bridge behavior to advance the migration.
- Preserve the existing Kotlin enforcement services and their behavior before considering refactors.
- Keep blocking, daily allowances, VPN, alarms, boot recovery, widgets, backup compatibility, PIN behavior, permissions, and accessibility disclosure intact unless the architecture explicitly changes them.
- Separate source-level implementation status from generated Android, emulator, and physical-device validation.

See the repository [`Readme.md`](../../Readme.md) for the workspace-level overview and the canonical architecture document for the complete migration plan.