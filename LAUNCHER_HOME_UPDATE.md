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

- **Home screen is a free grid** (4 columns by 6 rows, one page): see "Grid home" below. This replaced
  the earlier stacked list, arrange mode and the up/down widget drag (`HomeDragMath`, now deleted).
- **Pinned apps**: add from the drawer's long-press sheet, by dragging an app from the drawer onto the
  grid, or through Android's "Add to Home screen" (`PinShortcutActivity`, asks before adding).
- **App widgets**: hosted on the home screen (up to 6), picker; size by dragging the edge handles.
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
| `launcher_home_pinned`, `launcher_home_pinned_items` | Pinned apps shown or hidden, and the items (`launcher_home_pinned_size` is no longer used) |
| `launcher_home_grid` | Where each item sits on the grid home |
| `launcher_home_layout` | No longer used (it held the List | Grid choice during the beta) |
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

## Grid home

The home screen is a fixed 4 by 6 grid on one page, like a stock launcher. It shows the clock, status, task
and limits cards, pinned apps and shortcuts, and app widgets. Positions are saved in `launcher_home_grid`;
what exists still comes from the older settings (which cards are on, the pinned list, the hosted widgets).
The first time it opens, your old stacked layout is laid out on the grid in the same order. Anything that does
not fit is left off the grid with a message ("Resize or remove something to make room"); nothing is deleted.

- **Move**: long-press an item and drag. A copy follows your finger and an outline shows where it lands.
  Resting on another item moves it aside after about 0.15 s. Drag to the **Remove** bar at the top to take it
  off home (5 second Undo; a widget is only given back to Android after that).
- **Resize**: long-press a widget or card and let go without moving. Drag a handle on any edge in whole
  cells; whatever is in the way slides aside. Tap outside to close. A card keeps its natural height at the top
  of its cells.
- **Pinned apps**: long-press and let go without moving opens the app's menu (Remove from home, Edit home).
- **Edit home** (long-press empty space, or pinch): Widgets (add), Manage widgets (remove, including any that did
  not fit), Wallpaper, Style & options (the Customize home sheet).
- **Drag from the drawer**: long-press an app in the drawer and move; the drawer fades away and the icon
  follows you. Let go over a spot to pin it there. Long-press without moving still opens its menu.
- **Gone**: the stacked list layout, arrange mode, the up/down widget drag, the "Home layout" switch and the
  "Pinned grid size" option. Swipe up always opens the drawer and swipe down the notifications (the home screen
  no longer scrolls).

Not verified in the sandbox: the grid rules are unit tested (`HomeGridTest`, 38 tests). Everything else
(drawing, gestures, widget sizing) was compiled only by CI and has not been seen on a phone.

Please test on a phone:
- Open the launcher after updating: your old home appears on the grid, in the old order. If something is
  missing there should have been a "don't fit" message; Edit home > Manage widgets lists any hidden widget.
- Lift, move and drop icons, widgets and cards, onto free cells and onto other items; Remove and Undo; resize
  a widget; drag an app from the drawer onto the grid; Edit home rows; pinch opens Edit home.
- Swipe up from the middle of the home screen (over a widget too) opens the drawer; swipe down opens the
  notification shade; back closes resize mode.
- Handles at the screen's left or right edge may clash with the system back gesture.
