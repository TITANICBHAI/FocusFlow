# Repository Agent Instructions

## Mandatory FocusFlow architecture gate

Before changing anything under `artifacts/focusflow`, stop and read:

1. `artifacts/focusflow/AGENTS.md`
2. `artifacts/focusflow/ARCHITECTURE.md`

Do not implement or propose FocusFlow mobile architecture from memory, from a short summary, or from the current Expo files alone. The architecture document is the canonical target for the ongoing migration.

The current React Native/Expo code is a transitional source and behavior reference. The target mobile app is **pure Kotlin + Jetpack Compose with no JavaScript, TypeScript, React Native, Expo runtime, or React Native bridge**. Do not add new RN/Expo UI, new bridge modules, or new hybrid-only abstractions to move the product forward. If a requested change conflicts with the target architecture, resolve it against `artifacts/focusflow/ARCHITECTURE.md` before coding.

After reading the architecture, open the specific execution plan or tracker relevant to the requested slice. Keep source implementation, migration work, and Android device validation clearly separated.