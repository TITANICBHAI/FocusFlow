# FocusFlow React UI Plan — Companion Traceability Checklist

> **Related source:** `attached_assets/react_ui-plan_1787287243424.md`  
> **Primary source:** `attached_assets/master-plan_1787286671587.md`  
> **Primary execution tracker:** `artifacts/focusflow/react-ui plan.md`  
> **Companion source SHA-256:** `3d4bd28ad482cdc9b0871f5dc1f0d5dc19ac794c7d770091a886ce595c4a9f79`

## Relationship and source-quality note

This attachment describes the same FocusFlow React Native/Expo UI work as the primary plan: the prerequisite plus Phases 1–9 and the final checks. It is a related, differently formatted copy. The primary tracker remains the execution authority; this companion tracker exists so the second attachment is also traceable.

The companion attachment contains formatting damage and apparent snippet typos (for example, compressed imports and altered icon/module names). **Do not copy its code snippets blindly.** Validate implementation details against the primary source and the current repository before ticking a step.

## Mandatory agent contract

- [ ] Open both the primary tracker and this companion tracker before plan-covered work.
- [ ] Map every change to the matching `P#`/`P#.*` step in the primary tracker and the companion item below.
- [ ] Tick only after implementation and evidence are complete.
- [ ] Record the same evidence in both trackers when a step is completed.
- [ ] Run `tsc --noEmit` after each phase and record the result in both trackers.
- [ ] Re-check both trackers before reporting a phase or the overall task complete.
- [ ] Resolve every unchecked item or mark it `[blocked]` with a reason; never silently skip it.
- [ ] Preserve all restrictions from the primary tracker, including no file deletion, protected files, and `theme.*` references.

### Tick method

Replace `[ ]` with `[x]` only after the matching primary-plan item is also complete. Add:

`Evidence: <file or command> — <what was confirmed>`

If the companion and primary sources disagree, stop and validate against the current code and the primary source. Record the discrepancy under **Source discrepancies**.

## Companion progress dashboard

| Companion scope | Matching primary scope | Status | Typecheck |
|---|---|---|---|
| Prerequisite | P0 | [blocked] Install blocked by package firewall | ☐ |
| Phase 1: tab shell | P1 | [blocked] Typecheck blocked by incomplete install | ☐ |
| Phase 2: Defense tab | P2 | [blocked] Typecheck blocked by incomplete install | ☐ |
| Phase 3: Settings | P3 | [blocked] Typecheck blocked by incomplete install | ☐ |
| Phase 4: Focus tab | P4 | ☐ Not started | ☐ |
| Phase 5: Schedule | P5 | ☐ Not started | ☐ |
| Phase 6: Task modal | P6 | ☐ Not started | ☐ |
| Phase 7: Stats pager | P7 | ☐ Not started | ☐ |
| Phase 8: Main-tab swipe | P8 | ☐ Not started | ☐ |
| Phase 9: File association | P9 | ☐ Not started | ☐ |
| Final checks | F | ☐ Not started | ☐ |

---

## Prerequisite

- [blocked] **C0.1** Install `react-native-pager-view` before Phase 1.
  - Evidence: Dependency setup intentionally deferred per user instruction; no package manifest or lockfile changes were retained.

---

## Phase 1 — Tab navigation shell

- [x] **C1.1** Reorder tabs to Focus, Schedule, Defense, Stats, Settings.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — tab declaration order is Focus, Schedule, Defense, Stats, Settings.
- [x] **C1.2** Add the Defense tab and validate icon names against the installed Ionicons typings.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — Defense is registered with `shield-checkmark`/`shield-checkmark-outline`, matching the existing Ionicons usage in the app.
- [x] **C1.3** Remove the rendered floating `DarkModeToggle` while retaining the import.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — import remains and the floating rendered block is removed.
- [x] **C1.4** Leave side-menu components untouched.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — side-menu imports and render blocks remain unchanged.
- [blocked] **C1.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm install --frozen-lockfile --filter @workspace/focusflow --ignore-scripts` and `pnpm --filter @workspace/focusflow run typecheck` — install was blocked by the package firewall rejecting `shell-quote@1.8.3` (HTTP 403), leaving `artifacts/focusflow/node_modules` absent and `tsc` unavailable.

---

## Phase 2 — Defense tab

- [x] **C2.1** Create `app/(tabs)/defense.tsx` using only the specified existing app-context functions.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — reads app state and persists settings through the existing context APIs.
- [x] **C2.2** Implement Always-On Blocking, app-list navigation, daily allowance, and defense PIN gating.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — includes the enforcement switch, `/always-on` app-list route, `DailyAllowanceModal`, and PIN-gated disable flow.
- [x] **C2.3** Implement Keyword Blocker, Block Schedules, and PIN Protection navigation.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — rows navigate to `/keyword-blocker`, `/block-defense?tab=greyout`, and `/block-defense`.
- [x] **C2.4** Implement System Guard toggles and Aversion Deterrents toggles.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — theme-aware switches update the existing system guard, content guard, and aversion settings keys.
- [x] **C2.5** Preserve theme references, error boundary, and existing protected sub-pages.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — uses `theme.*`, wraps with the existing error boundary, and routes to existing protected pages without modifying them.
- [x] **C2.6** Register the Defense tab in the layout.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `Tabs.Screen` registers `name="defense"` in the Phase 1 tab order.
- [blocked] **C2.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — remains blocked because the authorized dependency install failed at the package firewall and FocusFlow has no local `node_modules`.

---

## Phase 3 — Settings cleanup

- [x] **C3.1** Add Appearance/Dark Mode as the first visible Settings section.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Appearance is the first Settings section and renders the existing `DarkModeToggle`.
- [x] **C3.2** Remove duplicate PIN, System Protection, Aversion Deterrents, and Daily Allowance UI.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — all duplicated defense sections and their modal/handler plumbing removed.
- [x] **C3.3** Preserve `setDailyAllowanceEntries` if still used.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — it is no longer used after Defense owns Daily Allowance, so the unused destructuring/import path was removed.
- [x] **C3.4** Confirm Dark Mode is rendered in Settings, not in the tab layout.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx`, `artifacts/focusflow/app/(tabs)/settings.tsx` — layout retains only the import; Settings contains the sole rendered instance.
- [blocked] **C3.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — remains blocked because the authorized dependency install failed at the package firewall and FocusFlow has no local `node_modules`.

---

## Phase 4 — Focus tab restructure

- [ ] **C4.1** Read `focus.tsx` fully before editing.
  - Evidence:
- [ ] **C4.2** Remove ring animation infrastructure and ring sizing while preserving needed subcomponents.
  - Evidence:
- [ ] **C4.3** Remove obsolete no-task/enforcement UI, state, helpers, TipsCard, and TIPS.
  - Evidence:
- [ ] **C4.4** Keep `useTaskTimer` unconditional and use one component return.
  - Evidence:
- [ ] **C4.5** Add the accessibility banner, standalone block panel, task session panel, progress UI, and required actions/modals.
  - Evidence:
- [ ] **C4.6** Preserve `StandaloneCountdown`, `PomodoroStrip`, and `SecondaryBtn`.
  - Evidence:
- [ ] **C4.7** Add requested styles without removing styles still used by subcomponents.
  - Evidence:
- [ ] **C4.8** Confirm exactly one return and no conditional hook calls.
  - Evidence:
- [ ] **C4.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 5 — Schedule cleanup

- [ ] **C5.1** Remove only the rendered schedule-health banner.
  - Evidence:
- [ ] **C5.2** Retain schedule-health derived variables.
  - Evidence:
- [ ] **C5.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 6 — Task creation redesign

- [ ] **C6.1** Read `EditTaskModal.tsx` fully before editing.
  - Evidence:
- [ ] **C6.2** Remove redundant field labels and replace duration entry with preset chips.
  - Evidence:
- [ ] **C6.3** Set color from priority and remove only the color-picker UI.
  - Evidence:
- [ ] **C6.4** Remove only the Pomodoro toggle and simplify allowed-app controls.
  - Evidence:
- [ ] **C6.5** Replace comma-separated tags with removable chips and submit-to-add input.
  - Evidence:
- [ ] **C6.6** Save `localTags` and preserve existing tags while editing.
  - Evidence:
- [ ] **C6.7** Collapse Notes by default unless existing content is present.
  - Evidence:
- [ ] **C6.8** Add and validate all supporting styles.
  - Evidence:
- [ ] **C6.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 7 — Stats internal swipe

- [ ] **C7.1** Add and validate the `react-native-pager-view` import and ref.
  - Evidence:
- [ ] **C7.2** Add filter ordering, pill-to-page navigation, and page-to-filter synchronization.
  - Evidence:
- [ ] **C7.3** Wrap only the four existing filter blocks; keep trailing content outside PagerView.
  - Evidence:
- [ ] **C7.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 8 — Main-tab swipe

- [ ] **C8.1** Add gesture-handler and pathname/router imports using the repository's actual package APIs.
  - Evidence:
- [ ] **C8.2** Add ordered tab paths, sub-page guard, horizontal threshold, and navigation behavior.
  - Evidence:
- [ ] **C8.3** Wrap the existing layout root without breaking Tabs or side-menu behavior.
  - Evidence:
- [ ] **C8.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 9 — `.focusflow` file association

- [ ] **C9A.1** Add and compose the Android manifest intent-filter plugin.
  - Evidence:
- [ ] **C9A.2** Validate content/octet-stream and file/`.focusflow` filter data against Android manifest conventions.
  - Evidence:
- [ ] **C9B.1** Add validated FileSystem, Linking, backup-service, and Alert imports.
  - Evidence:
- [ ] **C9B.2** Add `FileImportHost` inside `AppProvider`.
  - Evidence:
- [ ] **C9B.3** Handle initial/subsequent URIs, read failures, invalid files, confirmation, merge, and refresh.
  - Evidence:
- [ ] **C9C.1** Confirm `BackupEnvelope`, `parseBackupJson`, and `restoreFromJson` exports; flag missing functions.
  - Evidence:
- [ ] **C9.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Final verification gate

- [ ] **CF.1** Run `tsc --noEmit`; zero errors.
  - Evidence:
- [ ] **CF.2** Confirm Defense exists and is referenced.
  - Evidence:
- [ ] **CF.3** Confirm Dark Mode placement is correct.
  - Evidence:
- [ ] **CF.4** Confirm exactly one Focus return and no conditional hooks.
  - Evidence:
- [ ] **CF.5** Confirm `StandaloneCountdown`, `PomodoroStrip`, and `SecondaryBtn` remain.
  - Evidence:
- [ ] **CF.6** Confirm no file was deleted.
  - Evidence:
- [ ] **CF.7** Re-check the primary tracker and this companion tracker; no required item may be silently unchecked.
  - Evidence:

## Source discrepancies

- [ ] **D.1** Compare any differing instruction with the primary source and current code.
  - Evidence:
- [ ] **D.2** Record any confirmed discrepancy and resolution here:
  - Evidence:

## Completion record

- Started:
- Last updated:
- Completed:
- Overall status: ☐ Not started  ☐ In progress  ☐ Blocked  ☐ Complete
- Final verification owner: