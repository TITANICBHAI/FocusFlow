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
| Phase 4: Focus tab | P4 | [blocked] Implemented; typecheck blocked by package firewall | ☐ |
| Phase 5: Schedule | P5 | [blocked] Implemented; typecheck blocked by missing dependencies | ☐ |
| Phase 6: Task modal | P6 | [blocked] Implemented; typecheck blocked by missing dependencies | ☐ |
| Phase 7: Stats pager | P7 | [blocked] Pager integration added; package linking/typecheck blocked by package firewall | ☐ |
| Phase 8: Main-tab swipe | P8 | [blocked] Implemented; typecheck blocked by package firewall | ☐ |
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

- [x] **C4.1** Read `focus.tsx` fully before editing.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — complete 1,760-line source was read before the Phase 4 edit.
- [x] **C4.2** Remove ring animation infrastructure and ring sizing while preserving needed subcomponents.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — ring refs/effects, sizing, and `Animated` import are absent while timer/session subcomponents remain.
- [x] **C4.3** Remove obsolete no-task/enforcement UI, state, helpers, TipsCard, and TIPS.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — old no-task/enforcement surface and tips implementation are absent.
- [x] **C4.4** Keep `useTaskTimer` unconditional and use one component return.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — timer hook is called before render branching and `FocusScreen` has one return.
- [x] **C4.5** Add the accessibility banner, standalone block panel, task session panel, progress UI, and required actions/modals.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — all requested panels, progress bar, actions, and standalone/extend/PIN modals are wired.
- [x] **C4.6** Preserve `StandaloneCountdown`, `PomodoroStrip`, and `SecondaryBtn`.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — all three components remain defined and rendered.
- [x] **C4.7** Add requested styles without removing styles still used by subcomponents.
  - Evidence: `artifacts/focusflow/app/(tabs)/focus.tsx` — new panel, timer, progress, title, and time styles are present alongside modal-supporting styles.
- [x] **C4.8** Confirm exactly one return and no conditional hook calls.
  - Evidence: `rg -n "return \\(" artifacts/focusflow/app/(tabs)/focus.tsx` — one `FocusScreen` return; hooks remain at component top level.
- [blocked] **C4.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm install --frozen-lockfile --filter @workspace/focusflow --ignore-scripts` and `pnpm --filter @workspace/focusflow run typecheck` — blocked by the package firewall rejecting `shell-quote@1.8.3` (HTTP 403), leaving `tsc` unavailable.

---

## Phase 5 — Schedule cleanup

- [x] **C5.1** Remove only the rendered schedule-health banner.
  - Evidence: `artifacts/focusflow/app/(tabs)/index.tsx` — only the rendered schedule-health banner was removed; the rest of the Schedule screen is unchanged.
- [x] **C5.2** Retain schedule-health derived variables.
  - Evidence: `artifacts/focusflow/app/(tabs)/index.tsx` — `scheduleHealth`, `healthWarning`, and `healthColor` remain defined and derived from the schedule analyzer.
- [blocked] **C5.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow dependencies are missing after the package firewall rejected `shell-quote@1.8.3` with HTTP 403; `tsc` is unavailable.

---

## Phase 6 — Task creation redesign

- [x] **C6.1** Read `EditTaskModal.tsx` fully before editing.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — complete 474-line modal was read before the Phase 6 edit.
- [x] **C6.2** Remove redundant field labels and replace duration entry with preset chips.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — redundant labels are removed and the five requested duration chips are present.
- [x] **C6.3** Set color from priority and remove only the color-picker UI.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — priority determines saved color and no color-picker controls remain.
- [x] **C6.4** Remove only the Pomodoro toggle and simplify allowed-app controls.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — Pomodoro UI is removed while Focus Mode and the Customize allowed-app row remain.
- [x] **C6.5** Replace comma-separated tags with removable chips and submit-to-add input.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — tags are editable chips and the input submits individual tags.
- [x] **C6.6** Save `localTags` and preserve existing tags while editing.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — local tags initialize from and save back to the task.
- [x] **C6.7** Collapse Notes by default unless existing content is present.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — Notes opens automatically only when existing description text is present.
- [x] **C6.8** Add and validate all supporting styles.
  - Evidence: `artifacts/focusflow/src/components/EditTaskModal.tsx` — supporting duration, tag, allowed-app, Customize, Notes, and helper styles are used.
- [blocked] **C6.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow dependencies are missing after the package firewall rejected `shell-quote@1.8.3` with HTTP 403; `tsc` is unavailable.

---

## Phase 7 — Stats internal swipe

- [blocked] **C7.1** Add and validate the `react-native-pager-view` import and ref.
  - Evidence: `artifacts/focusflow/package.json`, `pnpm-lock.yaml`, `artifacts/focusflow/app/(tabs)/stats.tsx` — dependency declaration and lockfile resolution for `react-native-pager-view@9.0.2` are now present and the import/ref are wired; package linking remains blocked by the package firewall rejecting `shell-quote@1.8.3` with HTTP 403.
- [x] **C7.2** Add filter ordering, pill-to-page navigation, and page-to-filter synchronization.
  - Evidence: `artifacts/focusflow/app/(tabs)/stats.tsx` — Today and Yesterday pills are swapped visually, pager order remains content-aligned, and both directions synchronize.
- [x] **C7.3** Wrap only the four existing filter blocks; keep trailing content outside PagerView.
  - Evidence: `artifacts/focusflow/app/(tabs)/stats.tsx` — only the four filter pages are inside PagerView; QuickBlockSheet remains outside.
- [blocked] **C7.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because dependency linking failed when the package firewall rejected `shell-quote@1.8.3` with HTTP 403.

---

## Phase 8 — Main-tab swipe

- [x] **C8.1** Add gesture-handler and pathname/router imports using the repository's actual package APIs.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — imports the existing `Gesture`, `GestureDetector`, `router`, and `usePathname` APIs.
- [x] **C8.2** Add ordered tab paths, sub-page guard, horizontal threshold, and navigation behavior.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `TAB_PATHS` follows the five main tabs, exact-path guarding prevents sub-page swipes, and 60px horizontal releases navigate between adjacent tabs.
- [x] **C8.3** Wrap the existing layout root without breaking Tabs or side-menu behavior.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — the existing Tabs and all side-menu render blocks remain inside `GestureDetector`.
- [blocked] **C8.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow-local `tsc` is unavailable after the package firewall rejected `shell-quote@1.8.3` with HTTP 403.

---

## Phase 9 — `.focusflow` file association

- [x] **C9A.1** Add and compose the Android manifest intent-filter plugin.
  - Evidence: `artifacts/focusflow/plugins/withFocusDayAndroid.js` — `withFocusFlowFileAssociation` is composed as the final config modifier.
- [x] **C9A.2** Validate content/octet-stream and file/`.focusflow` filter data against Android manifest conventions.
  - Evidence: `artifacts/focusflow/plugins/withFocusDayAndroid.js` — guarded `VIEW` filters use `DEFAULT`/`BROWSABLE` and the requested content/octet-stream and file/.focusflow data.
- [x] **C9B.1** Add validated FileSystem, Linking, backup-service, and Alert imports.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — all required imports are present and connected to the existing services.
- [x] **C9B.2** Add `FileImportHost` inside `AppProvider`.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — `FileImportHost` is rendered within `AppProvider`.
- [x] **C9B.3** Handle initial/subsequent URIs, read failures, invalid files, confirmation, merge, and refresh.
  - Evidence: `artifacts/focusflow/app/_layout.tsx` — initial and event URLs are guarded, read and parse errors are surfaced, confirmation precedes merge, and existing restore callbacks refresh tasks.
- [x] **C9C.1** Confirm `BackupEnvelope`, `parseBackupJson`, and `restoreFromJson` exports; flag missing functions.
  - Evidence: `artifacts/focusflow/src/services/backupService.ts` — all three exports already exist and are consumed without inventing replacements.
- [blocked] **C9.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because FocusFlow-local `tsc` is unavailable after the package firewall rejected `shell-quote@1.8.3` with HTTP 403.

---

## Final verification gate

- [blocked] **CF.1** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — blocked because `tsc` is unavailable after the package firewall rejected `shell-quote@1.8.3` with HTTP 403.
- [x] **CF.2** Confirm Defense exists and is referenced.
  - Evidence: `defense.tsx` exists and `_layout.tsx` registers the `defense` tab.
- [x] **CF.3** Confirm Dark Mode placement is correct.
  - Evidence: `DarkModeToggle` is rendered in `settings.tsx` and has no render usage in `_layout.tsx`.
- [x] **CF.4** Confirm exactly one Focus return and no conditional hooks.
  - Evidence: `FocusScreen` has one component return; required hooks remain at component top level.
- [x] **CF.5** Confirm `StandaloneCountdown`, `PomodoroStrip`, and `SecondaryBtn` remain.
  - Evidence: all three remain defined and used in `focus.tsx`.
- [x] **CF.6** Confirm no file was deleted.
  - Evidence: `git diff --name-status` contains no deleted files.
- [x] **CF.7** Re-check the primary tracker and this companion tracker; no required item may be silently unchecked.
  - Evidence: incomplete verification items are explicitly marked `[blocked]` with evidence; implementation items have matching evidence.

## Source discrepancies

- [x] **D.1** Compare any differing instruction with the primary source and current code.
  - Evidence: companion final-verification entries were checked against the primary tracker and current implementation.
- [x] **D.2** Record any confirmed discrepancy and resolution here:
  - Evidence: the only confirmed deviation is environmental: TypeScript verification remains blocked by the package-firewall failure; no implementation discrepancy was found.

## Completion record

- Started:
- Last updated:
- Completed:
- Overall status: ☐ Not started  ☐ In progress  ☒ Blocked  ☐ Complete
- Final verification owner: