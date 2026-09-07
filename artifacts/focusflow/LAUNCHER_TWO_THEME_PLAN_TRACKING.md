# FocusFlow Launcher v3 — Implementation Tracker

This is the mutable status record for
`LAUNCHER_TWO_THEME_PLAN_1788803550916.md`. The plan is the authoritative
requirements brief and must remain unchanged. Update this file as work or
validation advances.

## Status legend

- `[ ]` Not started
- `[-]` In progress
- `[x]` Complete and validated
- `[!]` Blocked or needs a decision

## Execution order

### 1. Settings and shared home structure

- [ ] Add `launcherTheme` and `focusToolPackages` to the settings model/defaults.
- [ ] Sync both settings through the existing native SharedPrefs update path.
- [ ] Remove the home grid and dock, including their call sites and editing actions.
- [ ] Replace the productivity strip with the shared status pill.
- [ ] Replace the allowance strip with `buildTodaysLimits()` while keeping
  `loadAllowanceCardData()` unchanged.
- [ ] Add the shared home layout order and two independent bottom icon targets.
- Notes:

### 2. Classic home

- [ ] Implement the flat `#0E0E0E` background and non-blurred visual path.
- [ ] Apply the 72sp clock, status pill, flat cards, terse allowance values,
  and solid 56dp bottom icons.
- Notes:

### 3. Glassy home

- [ ] Apply wallpaper-backed rendering and the existing glass design tokens.
- [ ] Apply full allowance values and two independent frosted bottom icons.
- Notes:

### 4. Launcher configuration screen

- [ ] Remove clock-style, dock-order, and home-grid controls.
- [ ] Add side-by-side Classic/Glassy mini-previews with immediate switching.
- [ ] Show wallpaper controls only for Glassy.
- [ ] Reconfirm the existing default-launcher and Defense-tab entry behaviors
  remain untouched and functional.
- Notes:

### 5. Shared drawer filtering and Classic drawer

- [ ] Add one shared `DrawerFilter`/`filterDrawerApps()` implementation.
- [ ] Add the shared hidden-package behavior and filter chips.
- [ ] Implement the Classic 52dp list, 36dp icons, visible names, and 44dp
  non-blurred search bar.
- [ ] Extend the Classic long-press menu with Hide/Unhide and App Info.
- Notes:

### 6. Glassy drawer and Edit Mode

- [ ] Implement the 4-column Glassy grid, 42dp base icons, 44dp glass search,
  and reduced spacing.
- [ ] Add distinct Edit Mode with Cancel/Done and drag-handle badges.
- [ ] Add the Customize Drawer sheet, icon scale, and 4×5/5×6 grid settings.
- [ ] Route Lock app to the existing Always-On picker.
- [ ] Persist custom order with alphabetical fallback for new/unordered apps.
- Notes:

## Validation gate

- [ ] TypeScript/RN checks pass for settings and config-screen changes.
- [ ] Native/Kotlin source checks pass for both launcher themes and drawers.
- [ ] Generated Android source is synchronized only through the documented
  `install.sh` flow; `android/` was not edited directly.
- [ ] Classic path has no wallpaper/blur dependency.
- [ ] Glassy path reuses existing wallpaper and glass tokens.
- [ ] Shared drawer filters produce the same logical results in both styles.
- [ ] Full launcher validation checklist in the authoritative plan is complete.
- Notes:

## Decisions and blockers

- None recorded.