# Free-grid home screen (stock-launcher style)
Ticket: none (request: "I want that kind of" stock-launcher dragging, option C: free grid)

Status: APPROVED. Building one phase per round; see Progress.

## What "done" means
A person can long-press any item on the home screen, lift it, and drop it on any free cell of a
grid. Icons are 1x1, app widgets and FocusFlow cards span several cells and can be resized by
handles that snap to cells. Dropping on an occupied spot moves the others out of the way. The old
vertical-stack home keeps working until the grid has been tried on a real phone.

## What exists today (read from the code, commit a2fac7a)
- Home = `ScrollView` > `LinearLayout` of up to 6 blocks, in a saved order (`launcher_home_order`):
  clock, status pill, task card, limits, pinned apps, app widgets. Each built by a `build*` function
  in `LauncherActivity.kt` (5,092 lines).
- Pinned apps/shortcuts: one flat list (`HomePinnedItems`, `launcher_home_pinned_items`), drawn as a
  3/4/5-column grid inside the "pinned" block. Max 25.
- App widgets: `HomeWidgetEntries` (id, provider, size S/M/L = 110/180/260 dp tall, always full
  width). Max 6. Hosted by `AppWidgetHost` id `0x464F4357` (`HomeWidgetHost`).
- Dock (search bar + buttons) sits under the scroll area. Swipe up opens the drawer only when the
  home content cannot scroll further; swipe down opens notifications.
- Arrange mode (compact tiles) and the direct drag from commit a2fac7a work on the stack only.
- Only automatic check is CI (`build-tbtechsdev-apk.yml`: NewApi lint + unit tests + assemble).
  Nothing can be tried on a device from the build environment.

## Phases (risk first, each lands green on its own)

### Phase 0: The grid model, no UI (proves the hard maths first)
Verified when: unit tests for placement, nearest-cell, push-aside and migration all pass in CI.
Files: new `HomeGridModel.kt` (pure Kotlin, like `HomeDragMath`), new `HomeGridModelTest.kt`.

#### Step 0.1: Cells, spans, occupancy, nearest free area (Size: M)
- Acceptance: items have (kind, key, col, row, spanX, spanY); no overlaps; `findNearestArea(px, py, span)`
  returns the closest free cell block or null; out-of-range items are clamped on load.
- Verification: `gh workflow run build-tbtechsdev-apk.yml --ref main` (unit-test step); plus local run of
  the new test file against the JUnit stand-in used for `HomeDragMathTest`.
- Test seams: `HomeGridModel` public functions only.
- Blocked by: none

#### Step 0.2: Drop on an occupied area moves the others (Size: M)
- Acceptance: dropping an item where others sit pushes them down (then up) to the nearest free
  spots; if there is no room the drop is refused and nothing moves; result never overlaps.
- Verification: same as 0.1.
- Blocked by: 0.1

#### Step 0.3: Save format and one-time migration from the stack (Size: S)
- Acceptance: new pref `launcher_home_grid`, malformed lines skipped like `HomePinnedItems`;
  migration places today's blocks top to bottom in their saved order (cards full width, pinned icons
  one per cell, widgets full width at their S/M/L height) and never loses an item.
- Verification: same as 0.1.
- Blocked by: 0.1

### Phase 1: Draw the grid, behind a switch, no dragging yet
Verified when: with the switch on, home shows every existing item at its migrated cell, taps and
long-press menus still work, and with the switch off nothing has changed.
Files: new `HomeGridLayout.kt` (custom `ViewGroup`), `LauncherActivity.kt` (switch + wiring),
`showHomeCustomizeSheet` (the "Home layout: List | Grid (beta)" row).

#### Step 1.1: `HomeGridLayout` view group with icons (Size: M)
- Acceptance: cells sized from the screen (default 4 columns x 6 rows above the dock); pinned
  icons render and launch; their own long-press menu is kept.
- Verification: CI build, then a person checks it on a phone (cannot be done here).
- Blocked by: 0.3

#### Step 1.2: App widgets at their spans (Size: M)
- Acceptance: a widget fills spanX x spanY cells and gets `updateAppWidgetSize` for that area;
  default span comes from the provider's own size hints, never smaller than its minimum.
- Blocked by: 1.1

#### Step 1.3: FocusFlow cards as grid items (Size: M)
- Acceptance: clock, status, task and limits render at their spans using their existing styles and
  keep refreshing; a collapsed task card takes no cells.
- Blocked by: 1.1

### Phase 2: Stock-style drag and drop
Verified when: an item can be lifted and dropped on a free cell or an occupied one, and the result
is saved and survives reopening.
Files: new `HomeDragLayer.kt` (overlay that draws the lifted copy), `HomeGridLayout.kt`,
`LauncherActivity.kt` (long-press and touch routing).

#### Step 2.1: Overlay drag copy, drop-cell outline, drop animation (Size: M)
- Acceptance: long-press lifts a copy drawn above everything; the target cell block is outlined;
  letting go glides the copy into the cell, then the real view appears; saved.
- Blocked by: 1.1

#### Step 2.2: Push-aside with a short dwell (Size: M)
- Acceptance: hovering on occupied cells for about 150 ms makes the others slide to their new cells
  (spring); moving away puts them back; uses Phase 0.2.
- Blocked by: 2.1, 0.2

#### Step 2.3: Drop-target bar (Size: S)
- Acceptance: while dragging, a bar at the top shows "Remove" (unpins; for a FocusFlow card it hides
  it) and the item dropped there is removed with an undo toast.
- Blocked by: 2.1

### Phase 3: Resize handles for widgets and cards (Size: M)
- Acceptance: in edit state a lifted-and-released widget shows handles; dragging one resizes in whole
  cells, respecting the provider's min/max and `resizeMode`; saved.
- Blocked by: 1.2, 2.1

### Phase 4: Edit menu on empty space (Size: S)
- Acceptance: in grid mode, long-press on an empty cell opens a small menu: Widgets, Wallpaper,
  Style & options (the existing Customize home sheet). Arrange mode is not used in grid mode.
- Blocked by: 2.1

### Phase 5: Pages and edge-hover paging: DEFERRED (not in this plan; one fixed page now)

### Phase 6: Drag an app from the drawer onto the home grid
Verified when: with the switch on, long-pressing an app in the drawer and dragging lifts it, the
drawer slides away, and letting go over a free cell pins the app there.
Files: `HomeDragLayer.kt`, `LauncherActivity.kt` (drawer row long-press, `openDrawer`/`closeDrawer`),
`HomeGridLayout.kt`, `HomeGridModel.kt`.

#### Step 6.1: Start a drag from a drawer row (Size: M)
- Acceptance: a long-press that then moves lifts a copy of the app icon in the overlay and closes the
  drawer under it; a long-press that does not move still opens today's app menu (`showAppActions`);
  works only when the Grid switch is on.
- Verification: CI build; a person checks it on a phone.
- Blocked by: 2.1

#### Step 6.2: Drop it on the grid (Size: S)
- Acceptance: dropping on a free cell block pins the app there (same entry as "Pin to home"); an
  occupied spot uses the push-aside from 2.2; no room or already pinned gives a short message and the
  copy glides back; letting go outside the grid cancels.
- Verification: unit tests for the "no room" and "duplicate" outcomes in `HomeGridModelTest`; CI build.
- Blocked by: 6.1, 2.2

### Phase 7: Contract (Size: S, only after a person has used the grid on a phone)
- Acceptance: grid becomes the default; the stack, arrange mode and the old drag are removed.
- Blocked by: all phases above and an explicit go-ahead.

## Decision log
| Decision | Chosen option | Why |
| --- | --- | --- |
| Pure model first | `HomeGridModel` written and tested before any view | Same pattern as `HomeDragMath`; the only thing that can be proven without a device |
| Stack kept during the build | Grid behind a "Home layout" switch | 5,000-line launcher, CI is the only check, no device; a bad grid must not break the home screen |
| Drag copy in an overlay | Draw a copy in a top layer instead of moving the real view | Matches stock; not clipped by scroll or cell bounds |
| Rollout (asked) | Side by side behind a "Home layout: List \| Grid (beta)" switch | You chose it; the old home stays the fallback until the grid is tried on a phone |
| Paging (asked) | One fixed page, no scrolling; pages are a later plan | You chose it; matches stock, and swipe up can always open the drawer |
| What is on the grid (asked) | Icons, app widgets and FocusFlow cards, all placeable and resizable | You chose it; the stack holds all of these today |
| Drawer drag (asked) | Included as Phase 6 | You chose it; stock launchers drag from the drawer |

## Assumptions I am making (correct me before approval)
1. Grid is 4 columns x 6 rows on a phone in portrait, set from the screen size, above the dock. No
   landscape or tablet layout in this plan (the existing portrait home stays as it is today).
2. The dock (search bar, All apps button) stays exactly as it is and is not part of the grid.
3. Existing limits stay: up to 25 pinned icons and 6 widgets are no longer needed as separate caps;
   the grid's free cells are the only limit.
4. Migration never deletes anything; items that do not fit are kept in a short "not shown" list and
   reported once.
5. I push each finished phase to `main` with the switch off by default, run the debug build, and you
   try it on a phone between phases. No force pushes.
6. Drag from the drawer only works with the Grid switch on; with List it behaves as today.
7. Execution is one phase per round: I finish a phase, build it, report what was and was not
   verified, and start the next phase unless you say to stop.

## Out of scope (deliberately not doing)
Horizontal pages and edge paging, folders, landscape and tablet layouts, icon packs, changes to the
dock or the drawer's own layout, and anything in enforcement, allowance or blocking code.

## Not yet specified
- Phase 3: how to resize widgets whose provider gives no usable min/max (fog; researched when picked up).
- Phase 2.2: whether pushing should prefer down only or also up; the default is down then up.
- Accessibility: a non-drag way to move items (move to cell via a menu) for screen readers.
- Phase 0.3: exact default spans for FocusFlow cards (clock 4x2, status 2x1, task 4x2, limits 4x3 as a
  starting guess).

## Always / Never
- Always: keep the current home working until Phase 7; add a unit test with every pure-logic change;
  stage explicit paths; plain pushes only.
- Never: force push; remove the stack before a person has used the grid; change enforcement,
  allowance or blocking code; claim a phase works on a device that only compiled in CI.

## Progress
| Phase | Status |
| --- | --- |
| 0 Grid model | code and tests written (`HomeGrid.kt`, `HomeGridTest.kt`, 33 tests pass locally with a JUnit stand-in); CI result pending |
| 1 Draw the grid | not started |
| 2 Drag and drop | not started |
| 3 Resize | not started |
| 4 Edit menu | not started |
| 5 Pages | deferred, not in this plan |
| 6 Drawer drag | not started |
| 7 Contract | not started |
