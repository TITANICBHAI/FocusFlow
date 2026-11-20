# Launcher home update: what changed and how to check it

This round reworked the FocusFlow launcher's home screen, wallpaper and glass, plus a few related fixes.
Nothing here changes enforcement rules except the count-mode fix (item 1).

## Fixes

1. **Count-mode allowance (off by one).** The coordinator counts an open before the allowance check runs,
   so the last allowed open was judged "exhausted" and blocked. The Nth open now stays usable and the
   (N+1)th is blocked, as `fixes/PLAN.md` specifies. See `AllowanceCountPolicy` and its test.
2. **Interval allowance wording.** Launcher cards and the block screen now say when it comes back
   ("back at 3:40 PM", "back tomorrow 8:10 AM"). The small chip on a drawer icon keeps "time's up".
   See `AllowanceResetText`.
3. **Glass / black layer.** Glassiness 0 is plain black. Above that the wallpaper fades in over the first 15%
   and is never dimmed again; panels get clearer as it rises. All full-screen dark layers are gone.
   See `LauncherGlass`.
4. **Blurry backdrop.** Built-in and Adaptive wallpapers are now drawn at screen size.
5. **Status and navigation bars.** The launcher has its own theme (`Theme.FocusFlow.Launcher`) so the
   wallpaper runs behind both bars and the icons are white.
6. **Lock-screen wallpaper.** One shared routine (`PhoneWallpaper`) sets home and lock separately, checks
   each, retries the lock screen as a stream and as a combined call, and offers the system picker if it
   still did not take. Failure messages include the phone model and Android version.
7. **Swipe vs scroll.** The drawer / notification swipe is decided at touch-down, so scrolling the home
   screen no longer opens the drawer.
8. **Daily Allowance editor** is its own full-screen window (no app bottom bar showing through).

## New and changed behaviour

- **Drag a widget in place**: long-press a widget (clock, status, task card, limits, or the gaps around
  the pinned apps and app widgets) and it lifts. Drag it up or down, the others slide out of the way,
  let go and it stays. The home screen scrolls by itself near its top and bottom edge. See
  `HomeDragMath` and its test.
- **Arrange mode** (long-press empty space or the dock, or pinch the home screen): compact tiles, drag
  the handle to reorder, Hide / Show each widget, Done, or "Style & options". Still the place for
  Hide / Show and for screen-reader Move up / Move down.
- **Pinned apps grid**: 3x3, 4x4 or 5x5. Add from the drawer's long-press sheet, or through Android's
  "Add to Home screen" (`PinShortcutActivity`, asks before adding).
- **App widgets**: hosted on the home screen (up to 6), picker, small / medium / large height.
- **Wallpaper screen**: gallery photo is the first tile (+), dismissible notices, "use on phone" bottom
  sheet; the launcher's own wallpaper section offers the same.
- **Drawer**: sections (All apps, Focus tools, Limited time, Blocked) are removable; one left hides the row.
  List layout now honours the icon-size slider.
- **Text size**: new entries for Custom rules (7 screens), Usage data, FocusFlow file guide, Text Size,
  Stats Extra (Overview / Apps / Habits) and the launcher Wallpaper, Focus tools and Hidden apps screens.
- Two new wallpapers: Obsidian and Fog.

## Device-local settings added (not part of backups)

| Key | Meaning |
|---|---|
| `wallpaper_match_card_dismissed`, `wallpaper_phone_note_dismissed` | Closed notices on the Wallpaper screen |
| `launcher_home_order` | Home widget order |
| `launcher_home_pinned`, `launcher_home_pinned_size`, `launcher_home_pinned_items` | Pinned grid style, size, items |
| `launcher_home_widgets`, `launcher_home_widgets_items` | App widgets style and hosted widgets |
| `drawer_sections` | Which drawer sections are shown |

## Not verified in the sandbox

The Compose screens (Wallpaper, Daily Allowance editor, setup rows, Stats extras, text-size list) could not be
type-checked there. Pure logic (reset text, glass fade, count policy, phone-wallpaper results, pinned items,
widget entries) compiles and its tests pass. The launcher / service Kotlin was compiled against the Android 34
SDK with only missing third-party libraries reported.

## Please test on a phone (Android 10 and 11)

- Count mode with a limit of 1: the first open works, the second is blocked.
- Interval allowance used up: the home card and block screen show the reset time.
- Set a wallpaper to Home + lock; if the lock screen does not change, the system-picker offer should appear.
- Chrome "Add to Home screen" shows the confirm box (FocusFlow must be the default home app).
- Add a widget; long-press it; remove it.
- Long-press the clock, then drag it below the limits card: the others slide up, it settles, and the order
  is still there after leaving and reopening the launcher.
- Drag a tall block (limits, app widgets) with a long home screen: it should auto-scroll near the top and
  bottom edge, and letting go must not open the drawer or the notification shade.
- Lift a widget and let go without moving: nothing changes.
- Long-press empty space or the dock, and pinch: both still open arrange mode; drag a tile; Hide then Show a
  widget; "Style & options" opens Customize home.
- Long-press a pinned app and an app widget: their own menus still open (not a drag).

## Grid home layout (beta), step 1 of the free-grid plan

Customize home now has **Home layout: List | Grid (beta)**. List is the default and is unchanged. Grid shows
the same things (clock, status, task, limits, pinned apps and shortcuts, app widgets) on a fixed 4 by 6 grid.
Positions are saved in `launcher_home_grid`; what exists still comes from the old settings, so switching back
and forth loses nothing. Anything that does not fit is left off the grid with a message and stays on List.

Step 1 drew the grid. Step 2 (below) adds dragging. You cannot resize on it yet. Long-press on empty space opens
Customize home.

Not verified in the sandbox: nothing in the grid view could be compiled or run here, only its pure rules
(`HomeGrid`, 33 tests). CI compiles it; nobody has seen it on a phone.

Please test on a phone:
- Customize home > Home layout > Grid (beta): the clock, status and pinned apps appear on a grid; switching back
  to List brings the old home screen back exactly.
- Add a widget while on Grid: it appears full width, and tapping it works.
- The task card and limits card reserve their cells even when empty (so they do not jump around when a task
  starts). If the limits card is missing and a message said so, that is the "does not fit" case.
- Rotate or open the notification shade and come back: nothing moves.

### Step 2: drag on the grid

Long-press any item on the Grid layout and it lifts: a copy follows your finger, a soft outline shows the cells it
would take, and anything in the way slides aside once you rest on it for a moment (about 0.15 s). Let go to
drop. Drag to the **Remove** bar at the top to take it off home; an **Undo** bar shows for 5 seconds (an app
widget is only given back to Android after that). Long-press and let go without moving opens the item's old
menu (pinned app menu, widget menu) as before.

Not verified in the sandbox: this was not compiled or run here (CI only) and has not been seen on a phone.

Please test on a phone (Grid layout):
- Lift an icon, drop it on an empty cell: it lands exactly there and is still there after leaving and returning.
- Drop an icon onto another icon, resting a moment first: the other slides down; dragging quickly across does
  not move others.
- Lift the clock card and the app widget; drop them on empty space and on other items.
- Drag something to the Remove bar and let go: it goes, and Undo brings it back in the same spot. For a widget,
  Undo within 5 seconds keeps its settings.
- Lift and let go without moving: the old menu opens. Lift and drop in the same place: nothing changes.
- Dropping a big item where nothing can make room: it returns to where it was.
- While dragging, nothing scrolls and the drawer or notification shade never opens.
