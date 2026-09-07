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

- [x] Add `launcherTheme` and `focusToolPackages` to the settings model/defaults.
- [x] Sync both settings through the existing native SharedPrefs update path.
- [x] Remove the home grid and dock, including their call sites and editing actions.
- [x] Replace the productivity strip with the shared status pill.
- [x] Replace the allowance strip with `buildTodaysLimits()` while keeping
  `loadAllowanceCardData()` unchanged.
- [x] Add the shared home layout order and two independent bottom icon targets.
- Notes: Native source review completed; generated Android/Kotlin/device validation remains
  pending because this workspace does not contain a generated `android/` Gradle project.

### 2. Classic home

- [x] Implement the flat `#0E0E0E` background and non-blurred visual path.
- [x] Apply the 72sp clock, status pill, flat cards, terse allowance values,
  and solid 56dp bottom icons.
- Notes: Matches the Classic reference's flat black canvas, thin large clock,
  compact pill, and independent solid circles. The native font remains the
  platform sans font; no bundled reference font was available.

### 3. Glassy home

- [x] Apply wallpaper-backed rendering and the existing glass design tokens.
- [x] Apply full allowance values and two independent frosted bottom icons.
- Notes: Matches the Glassy reference's wallpaper/scrim, frosted cards, full
  allowance sentences, and separated bottom actions. Android blur is represented
  by the existing translucent glass tokens because no new blur dependency was added.

### 4. Launcher configuration screen

- [x] Remove clock-style, dock-order, and home-grid controls.
- [x] Add side-by-side Classic/Glassy mini-previews with immediate switching.
- [x] Show wallpaper controls only for Glassy.
- [x] Reconfirm the existing default-launcher and Defense-tab entry behaviors
  remain untouched and functional.
- Notes: Previews show the shared clock/pill/cards/actions at reduced scale.
  Wallpaper remains persisted but is intentionally ignored by Classic.

### 5. Shared drawer filtering and Classic drawer

- [x] Add one shared `DrawerFilter`/`filterDrawerApps()` implementation.
- [x] Add the shared hidden-package behavior and filter chips.
- [x] Implement the Classic 52dp list, 36dp icons, visible names, and 44dp
  non-blurred search bar.
- [x] Extend the Classic long-press menu with Hide/Unhide and App Info.
- Notes: Both themes read the same filter and hidden-package keys. Classic keeps
  the dense list and minimal long-press dialog; blocked apps still open the block overlay.

### 6. Glassy drawer and Edit Mode

- [x] Implement the 4-column Glassy grid, 42dp base icons, 44dp glass search,
  and reduced spacing.
- [x] Add distinct Edit Mode with Cancel/Done and drag-handle badges.
- [x] Add the Customize Drawer sheet, icon scale, and 4×5/5×6 grid settings.
- [x] Route Lock app to the existing Always-On picker.
- [x] Persist custom order with alphabetical fallback for new/unordered apps.
- Notes: The Glassy editor keeps the written shared filter-chip contract even
  though the supplied editor image uses category labels; edit mode is entered
  from the header pencil action and supports drag, scale, grid, hide, and lock.

## Validation gate

- [x] TypeScript/RN checks pass for settings and config-screen changes.
- [-] Native/Kotlin source checks pass for both launcher themes and drawers.
- [ ] Generated Android source is synchronized only through the documented
  `install.sh` flow; `android/` was not edited directly.
- [ ] Classic path has no wallpaper/blur dependency.
- [ ] Glassy path reuses existing wallpaper and glass tokens.
- [ ] Shared drawer filters produce the same logical results in both styles.
- [ ] Full launcher validation checklist in the authoritative plan is complete.
- Notes:

## Decisions and blockers

- None recorded.