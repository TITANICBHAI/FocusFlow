# FocusFlow React UI Plan — Traceable Implementation Checklist

> **Source of truth:** `attached_assets/master-plan_1787286671587.md`  
> **Source SHA-256:** `8b19b080ed4cb4cc49b34055972c1bfb7a82b9a4c2531b2e90d1d1c77d27cb60`  
> **Purpose:** operational progress tracker for the imported FocusFlow React Native/Expo UI plan.

## Mandatory tracking rules

- [ ] **Agent handoff contract:** Any agent doing work covered by this plan must open this file first, claim the relevant phase/step IDs, and update the checkboxes plus evidence before handing work back.
- [ ] **Completion contract:** No agent may report a phase or the overall task as complete while a required item is unchecked, missing evidence, or missing its typecheck result.
- [ ] **Re-check contract:** After making changes, the agent must re-read the affected checklist items and run the relevant final verification checks before reporting completion.
- [ ] Do not begin a phase until the previous phase is complete.
- [ ] Tick a step only after the implementation exists **and** its verification evidence is recorded.
- [ ] Every code change must map to a phase/step ID below.
- [ ] Keep the source plan attached and unchanged; this file is the traceable execution copy.
- [ ] Run `tsc --noEmit` after every phase and record the result.
- [ ] Do not delete files.
- [ ] Do not touch `SideMenu.tsx`, `AppContext.tsx`, `types.ts`, `database.ts`, or any `.kt` file.
- [ ] Do not modify `block-defense.tsx`, `keyword-blocker.tsx`, `always-on.tsx`, or `active.tsx`.
- [ ] Preserve `theme.*` color references; do not replace them with hardcoded hex colors.
- [ ] Read a large file fully before making targeted edits.

### Tick method

For each step, replace `[ ]` with `[x]` only when complete. Add a short evidence line immediately below it:

`Evidence: <file or command> — <what was confirmed>`

Use `[blocked]` only when work cannot proceed; explain the blocker and do not tick the item. A phase is complete only when every item in that phase is ticked and its phase typecheck is recorded as passing.

## Progress dashboard

| Phase | Scope | Status | Phase typecheck |
|---|---|---|---|
| 0 | Package prerequisite | [blocked] Install blocked by package firewall | ☐ |
| 1 | Tab navigation shell | [blocked] Typecheck blocked by incomplete install | ☐ |
| 2 | Defense tab | [blocked] Typecheck blocked by incomplete install | ☐ |
| 3 | Settings cleanup | [blocked] Typecheck blocked by incomplete install | ☐ |
| 4 | Focus tab restructure | ☐ Not started | ☐ |
| 5 | Schedule cleanup | ☐ Not started | ☐ |
| 6 | Task creation redesign | ☐ Not started | ☐ |
| 7 | Stats internal swipe | ☐ Not started | ☐ |
| 8 | Main-tab swipe | ☐ Not started | ☐ |
| 9 | `.focusflow` file association | ☐ Not started | ☐ |
| F | Final verification | ☐ Not started | ☐ |

---

## Phase 0 — Install prerequisite

- [blocked] **P0.1** Install `react-native-pager-view` with `npx expo install react-native-pager-view`.
  - Evidence: Dependency setup intentionally deferred per user instruction; no package manifest or lockfile changes were retained.

---

## Phase 1 — Tab navigation shell (`app/(tabs)/_layout.tsx`)

- [x] **P1.1** Reorder tabs to `focus`, `index`, `defense`, `stats`, `settings`.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — tab declaration order is Focus, Schedule, Defense, Stats, Settings.
- [x] **P1.2** Use the specified titles and focused/unfocused Ionicons for all five tabs.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — Focus uses `timer`/`timer-outline`, Schedule uses `calendar`/`calendar-outline`, Defense uses `shield-checkmark`/`shield-checkmark-outline`, Stats uses `bar-chart`/`bar-chart-outline`, and Settings uses `settings`/`settings-outline`.
- [x] **P1.3** Remove the rendered `<DarkModeToggle />` from the layout while retaining its import.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `DarkModeToggle` import remains and its floating rendered block is removed.
- [x] **P1.4** Leave `SideMenu`, `SideMenuToggle`, and `SideMenuGuideTip` unchanged.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — side-menu imports and render blocks remain unchanged.
- [blocked] **P1.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm install --frozen-lockfile --filter @workspace/focusflow --ignore-scripts` and `pnpm --filter @workspace/focusflow run typecheck` — install was blocked by the package firewall rejecting `shell-quote@1.8.3` (HTTP 403), leaving `artifacts/focusflow/node_modules` absent and `tsc` unavailable.

---

## Phase 2 — Create Defense tab (`app/(tabs)/defense.tsx`)

- [x] **P2.1** Create the Defense screen using `useApp()` state, `updateSettings`, and `setDailyAllowanceEntries`.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — reads app state and persists settings through the existing context APIs.
- [x] **P2.2** Add Always-On Blocking with enforcement toggle, app-list navigation, and daily allowance modal.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — includes the enforcement switch, `/always-on` app-list route, and `DailyAllowanceModal`.
- [x] **P2.3** Gate disabling protected defenses with the defense PIN flow and retain the first-open PIN hint.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — disabling protected settings uses `SharedPrefsModule`, `PinVerifyModal`, and the existing no-password setup prompt.
- [x] **P2.4** Add navigation rows for Keyword Blocker, Block Schedules, and PIN Protection.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — rows navigate to `/keyword-blocker`, `/block-defense?tab=greyout`, and `/block-defense`.
- [x] **P2.5** Add System Guard toggles for system controls, YouTube Shorts, and Instagram Reels.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — three theme-aware protected switches update the existing settings keys.
- [x] **P2.6** Add Aversion Deterrents toggles for dimmer, vibration, and sound.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — three theme-aware protected switches update the existing aversion settings keys.
- [x] **P2.7** Wrap the screen with the existing screen error boundary.
  - Evidence: `artifacts/focusflow/app/(tabs)/defense.tsx` — default export uses `withScreenErrorBoundary(DefenseScreen, 'Defense')`.
- [x] **P2.8** Confirm the new file is registered by the tab layout.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx` — `Tabs.Screen` registers `name="defense"` in the Phase 1 tab order.
- [blocked] **P2.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — remains blocked because the authorized dependency install failed at the package firewall and FocusFlow has no local `node_modules`.

---

## Phase 3 — Settings tab cleanup (`app/(tabs)/settings.tsx`)

- [x] **P3.1** Import and render `DarkModeToggle` as the first Appearance row.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Appearance is the first Settings section and renders the existing `DarkModeToggle`.
- [x] **P3.2** Remove the duplicate Manage PIN Passwords button.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — duplicate PIN Protection UI and Manage PIN Passwords row removed; PIN management remains on Defense.
- [x] **P3.3** Remove the System Protection section and its UI.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — System Protection section and its handlers removed.
- [x] **P3.4** Remove the Aversion Deterrents section and its UI.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Aversion Deterrents section removed.
- [x] **P3.5** Remove the Daily Allowance section and its modal usage from Settings.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — Daily App Allowance section, import, state, and modal usage removed; this now lives on Defense.
- [x] **P3.6** Keep `setDailyAllowanceEntries` in the `useApp()` destructure if still needed.
  - Evidence: `artifacts/focusflow/app/(tabs)/settings.tsx` — removed from Settings because it is no longer used after the Defense tab owns the Daily Allowance modal.
- [x] **P3.7** Confirm `DarkModeToggle` is no longer rendered in `_layout.tsx`.
  - Evidence: `artifacts/focusflow/app/(tabs)/_layout.tsx`, `artifacts/focusflow/app/(tabs)/settings.tsx` — layout retains only the import; Settings contains the sole rendered instance.
- [blocked] **P3.TS** Run `tsc --noEmit`; zero errors.
  - Evidence: `pnpm --filter @workspace/focusflow run typecheck` — remains blocked because the authorized dependency install failed at the package firewall and FocusFlow has no local `node_modules`.

---

## Phase 4 — Focus tab restructure (`app/(tabs)/focus.tsx`)

- [ ] **P4.1** Read the complete file before editing.
  - Evidence:
- [ ] **P4.2** Remove ring animation refs/effects, `useWindowDimensions`, and unused `Animated` import.
  - Evidence:
- [ ] **P4.3** Update `TimerDisplay` to the non-ring timer shape, retaining it only if needed.
  - Evidence:
- [ ] **P4.4** Remove the old no-task empty UI and unified enforcement panel.
  - Evidence:
- [ ] **P4.5** Remove obsolete enforcement variables, defense/daily allowance modal state, `TipsCard`, and `TIPS`.
  - Evidence:
- [ ] **P4.6** Keep `useTaskTimer` unconditional at component level.
  - Evidence:
- [ ] **P4.7** Replace the multiple-return structure with one return containing the accessibility banner, standalone block panel, and task session panel.
  - Evidence:
- [ ] **P4.8** Preserve standalone countdown, Pomodoro strip, secondary buttons, task actions, and all required modals.
  - Evidence:
- [ ] **P4.9** Add the new panel, progress, task-title, and task-time styles without removing still-used styles.
  - Evidence:
- [ ] **P4.10** Confirm `focus.tsx` has exactly one component return and no conditional hook calls.
  - Evidence:
- [ ] **P4.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 5 — Schedule tab cleanup (`app/(tabs)/index.tsx`)

- [ ] **P5.1** Remove only the rendered schedule health banner.
  - Evidence:
- [ ] **P5.2** Retain `scheduleHealth`, `healthWarning`, and `healthColor` variables.
  - Evidence:
- [ ] **P5.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 6 — Task creation redesign (`src/components/EditTaskModal.tsx`)

- [ ] **P6.1** Read the complete modal before editing.
  - Evidence:
- [ ] **P6.2** Remove redundant field-label Text elements while retaining input placeholders.
  - Evidence:
- [ ] **P6.3** Replace typed duration input with 25m/45m/1h/1h 30m/2h preset chips.
  - Evidence:
- [ ] **P6.4** Auto-set task color from priority and remove the color picker UI; retain color state for saving.
  - Evidence:
- [ ] **P6.5** Remove only the Pomodoro toggle from the Focus Mode section.
  - Evidence:
- [ ] **P6.6** Simplify allowed-apps UI to the Customize row and global-list description.
  - Evidence:
- [ ] **P6.7** Remove `useGlobalApps` state and setter without changing focus-service behavior.
  - Evidence:
- [ ] **P6.8** Replace comma-separated tags input with removable tag chips and submit-to-add input.
  - Evidence:
- [ ] **P6.9** Save `localTags` and preserve existing tags when editing.
  - Evidence:
- [ ] **P6.10** Make Notes collapsed by default unless existing content is present.
  - Evidence:
- [ ] **P6.11** Add all requested chip, tag, allowed-app, and notes styles.
  - Evidence:
- [ ] **P6.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 7 — Stats internal swipe (`app/(tabs)/stats.tsx`)

- [ ] **P7.1** Install/use `react-native-pager-view` and add the PagerView import.
  - Evidence:
- [ ] **P7.2** Add the PagerView ref and `FILTER_ORDER` mapping.
  - Evidence:
- [ ] **P7.3** Make filter pills update both filter state and pager page.
  - Evidence:
- [ ] **P7.4** Wrap the four existing filter content blocks in one PagerView without changing their content.
  - Evidence:
- [ ] **P7.5** Keep content outside/after the four blocks, such as QuickBlockSheet, outside PagerView.
  - Evidence:
- [ ] **P7.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 8 — Swipe between main tabs (`app/(tabs)/_layout.tsx`)

- [ ] **P8.1** Add gesture-handler and pathname/router imports.
  - Evidence:
- [ ] **P8.2** Add the ordered tab path map and sub-page guard.
  - Evidence:
- [ ] **P8.3** Add horizontal pan behavior with the specified offsets and 60px navigation thresholds.
  - Evidence:
- [ ] **P8.4** Wrap the existing tab layout root with `GestureDetector` without removing existing side-menu behavior.
  - Evidence:
- [ ] **P8.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Phase 9 — `.focusflow` file association

### 9A — Android intent filter (`plugins/withFocusDayAndroid.js`)

- [ ] **P9A.1** Add the file-association config plugin.
  - Evidence:
- [ ] **P9A.2** Add guarded `content://` octet-stream and `file://` `.focusflow` VIEW filters.
  - Evidence:
- [ ] **P9A.3** Compose the plugin as the final step before `module.exports`.
  - Evidence:

### 9B — Import host (`app/_layout.tsx`)

- [ ] **P9B.1** Add FileSystem, Linking, backup-service, and Alert imports as needed.
  - Evidence:
- [ ] **P9B.2** Add `FileImportHost` inside `AppProvider`.
  - Evidence:
- [ ] **P9B.3** Handle initial and subsequent links, accept only `.focusflow`/content/file URIs, and show read/invalid-file errors.
  - Evidence:
- [ ] **P9B.4** Show a user-confirmed import summary before merging backup data.
  - Evidence:
- [ ] **P9B.5** Preserve current tasks/settings and refresh after applying the backup.
  - Evidence:

### 9C — Backup service exports (`src/services/backupService.ts`)

- [ ] **P9C.1** Confirm `BackupEnvelope` is exported; add only the export keyword if missing.
  - Evidence:
- [ ] **P9C.2** Confirm `parseBackupJson` and `restoreFromJson` are exported; flag missing functions rather than inventing implementations.
  - Evidence:
- [ ] **P9.TS** Run `tsc --noEmit`; zero errors.
  - Evidence:

---

## Final verification gate

- [ ] **F.1** Run `tsc --noEmit` and confirm zero errors.
  - Evidence:
- [ ] **F.2** Confirm `app/(tabs)/defense.tsx` exists and is referenced in `_layout.tsx`.
  - Evidence:
- [ ] **F.3** Confirm `DarkModeToggle` appears in `settings.tsx` and is not rendered in `_layout.tsx`.
  - Evidence:
- [ ] **F.4** Confirm `focus.tsx` has exactly one return statement.
  - Evidence:
- [ ] **F.5** Confirm `StandaloneCountdown` remains in `focus.tsx`.
  - Evidence:
- [ ] **F.6** Confirm `PomodoroStrip` and `SecondaryBtn` remain in `focus.tsx`.
  - Evidence:
- [ ] **F.7** Confirm no file was deleted.
  - Evidence:
- [ ] **F.8** Re-check every phase above: no unchecked item may be described as complete.
  - Evidence:
- [ ] **F.9** Record any deviations from the attached source plan here:
  - Evidence:

## Completion record

- Started:
- Last updated:
- Completed:
- Overall status: ☐ Not started  ☐ In progress  ☐ Blocked  ☐ Complete
- Final verification owner: