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

- **Arrange mode** (long-press or pinch the home screen): compact tiles, drag the handle to reorder,
  Hide / Show each widget, Done, or "Style & options".
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
- Long-press and pinch both open arrange mode; drag a tile; Hide then Show a widget.
