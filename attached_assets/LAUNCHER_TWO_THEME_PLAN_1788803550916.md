# FocusFlow Launcher v3 — Two-Theme System (Classic + Glassy)

> Agent execution plan. Native Kotlin work is in
> `android-native/app/src/main/java/com/tbtechs/focusflow/services/LauncherActivity.kt`
> (synced to `android/` by `install.sh` — never edit `android/` directly).
> RN config-screen work is in `app/home-launcher.tsx`.

---

## What this supersedes, and what it reuses

The current `LauncherActivity.kt` already implements the earlier "Focused
Glass" redesign in full — clock widget, search bar, focus session card,
productivity strip, home icon grid, dock, drawer with sections, all the
accessibility/animation guards (`animationsEnabled`, `contrastRatio`,
`rippleForeground`). It also already has an **allowance strip**
(`buildAllowanceStrip` / `buildAllowanceCard` / `loadAllowanceCardData`) —
a feature not in the original plan, added since.

This plan **removes** the home icon grid and dock entirely (no more
`buildDockArea`, `refreshHomeGrid`, `addHomeGridIcon`, `refreshDock`,
`addDockIcon`, `showHomeIconMenu`, `showDockIconMenu`, `addToHome`,
`removeFromHome`, `addToDock`, `removeFromDock` — delete all of these and
their call sites) and **replaces** the home screen with the card-based
layout in the reference images: clock → status pill → Current Task card →
Today's Limits → two standalone icons, no grid, no dock.

It **reuses** `buildFocusSessionCard`/`refreshFocusCard` (restyled, not
rebuilt — content and refresh logic are already correct) and
`loadAllowanceCardData()` (the data is already there; only the rendering
changes). It **extends** the drawer with functional filter chips and, for
Glassy only, a dedicated edit mode.

Two things you might expect to need are already done — confirmed by reading
the current code, not assumed:

- **"Set as Default" disappearing when already default** — already works.
  `home-launcher.tsx` calls `SharedPrefsModule.isDefaultLauncher()` and the
  button is already wrapped in `{!isDefault && (...)}`.
- **Configure Home Launcher entry point from the Defense tab** — already
  wired. The screen's own header comment confirms: *"Accessible from: Block
  Enforcement → Home Launcher section → 'Configure Home Launcher'."*

Neither needs any work in this plan.

---

## §1  Settings & shared data model

`src/data/types.ts` — add to `AppSettings`:

```typescript
launcherTheme: 'classic' | 'glassy';      // NEW — the whole-theme choice
focusToolPackages: string[];              // NEW — user-curated "Focus Tools" whitelist (§8)
// launcherClockStyle, launcherWallpaperUri already exist — keep both,
// see §2 for how they map onto the two themes.
```

Add `launcherTheme: 'glassy'` to `DEFAULT_SETTINGS` (glassy is the visually
richer default for first-run; Classic is the deliberate opt-in for users who
want the sparse, high-density option — flip this default if you'd rather
lead with Classic instead).

`focusToolPackages: []` default — empty until the user configures it (§8).

Both fields sync to native the same way `launcherClockStyle` already does —
find that existing sync call in `updateSettings`'s `Promise.all` block and
add `launcherTheme`/`focusToolPackages` alongside it, same pattern, same
SharedPrefs-backed native module.

---

## §2  Config screen overhaul — `app/home-launcher.tsx`

**Remove:**
- The "Clock style" segmented control (Digital/Analog) — Classic's clock is
  fixed to the spec in §4 (72sp thin digital); Glassy's is fixed to the spec
  in §5. Theme choice now implies clock style; no separate axis.
- "Dock order" section and its `OrderedAppRow` list — no more dock.
- "Home grid order" section and its app-toggle list — no more grid.
- The single `<LauncherPreview pinnedApps={...} dockApps={...} .../>` call.

**Keep, relocate under Glassy-specific settings:**
- Wallpaper picker (`launcherWallpaperUri`, `handlePickWallpaper`,
  `handleClearWallpaper`) — Classic ignores wallpaper entirely (always flat
  `#0E0E0E`), so only show this row when `launcherTheme === 'glassy'`.

**Add: side-by-side theme picker with live mini-previews.**

```tsx
<View style={styles.themeRow}>
  <ThemePreviewCard
    label="Classic"
    active={settings.launcherTheme === 'classic'}
    onPress={() => void update({ launcherTheme: 'classic' })}
    variant="classic"
  />
  <ThemePreviewCard
    label="Glassy"
    active={settings.launcherTheme === 'glassy'}
    onPress={() => void update({ launcherTheme: 'glassy' })}
    variant="glassy"
  />
</View>
```

`ThemePreviewCard` is a small, static, non-interactive mock of each home
screen at reduced scale (roughly 140×280dp) — not a live render of the real
native launcher (that would need a costly native→RN bridge just for a
thumbnail). Build it as a plain RN view: a tiny clock, tiny pill, tiny two
stacked cards, two tiny circles at the bottom, styled per each theme's real
spec below. Tapping either card sets `launcherTheme` immediately — no
separate "Apply" step, consistent with "user can switch them anytime."

Because the actual home screen only re-renders when the Activity resumes,
switching theme here won't visually update until the user next presses
Home — that's expected and fine; no need to force a native refresh from
this screen.

---

## §3  Home screen — shared structure (both themes)

`buildHomeLayout()`'s new column order, replacing the current one:

```
buildClockWidget()          ← restyled per theme, §4/§5
buildStatusPill()            ← NEW, replaces buildProductivityStrip's chip row
buildFocusSessionCard()      ← REUSE existing method + refreshFocusCard(), restyled only
buildTodaysLimits()           ← NEW, replaces buildAllowanceStrip, reuses loadAllowanceCardData()
buildSearchBar()              ← unchanged from current implementation
[spacer — no grid]
buildTwoIconRow()              ← NEW, replaces buildDockArea() entirely
```

Delete `buildProductivityStrip`, `buildChip`, `refreshProductivityStrip` —
their one useful piece of data (blocked count + Always-On status) moves into
the new single status pill below. Delete `buildAllowanceStrip`,
`buildAllowanceCard` as separate methods — their logic is absorbed into
`buildTodaysLimits()`, which calls the **existing, unchanged**
`loadAllowanceCardData()` for data.

### `buildStatusPill()`

One pill, not a row of chips. Content: `"{N} blocked • {status}"` where `N`
comes from `getBlockedPackages().size` (existing method, unchanged) and
`{status}` is `"Always-On"` when `PREF_ALWAYS_BLOCK` is true, or the current
productivity strip's other conditions (standalone active, next task) as a
fallback — reuse that existing condition logic, just render it as the single
`{status}` segment of one pill instead of separate chips.

### `buildTwoIconRow()`

Two icons, **not wrapped in a shared pill or bar** — this is the "capsule
was the problem" fix from your reference material. Each icon sits in its own
small circular touch target, independently positioned left/right, with
visible space between them (the wallpaper/background shows through). Reuses
the existing All-Apps-dots-icon and FocusFlow-F-icon `onDraw` logic and
`contentDescription`s from the current dock code — only the **container**
changes (no shared background, no shared frame).

---

## §4  Classic theme — exact spec

Applies when `launcherTheme === 'classic'`.

| Element | Spec |
|---|---|
| Background | Flat `#0E0E0E`. No wallpaper, no gradient, no image — same for every user, every device. |
| Clock | `72sp`, `Typeface.DEFAULT` (thin/regular weight — not bold), pure white, centered, **no stroke, no shadow, no blur**. Date row above it, small, muted grey. |
| Status pill | Background `#1E1E1E`, border `1dp #2A2A2A`, height `32dp`, corner radius = half height (fully rounded), text 13sp, centered icon + text. |
| Cards (Current Task, Today's Limits) | Background `#18181A`, border `1dp #252525`, corner radius `20dp`. **No blur anywhere** — this is the deliberate performance choice from your reference material (backdrop-blur was costing ~45fps on mid-range hardware; flat fills hold 120fps). |
| Current Task — title | `20sp`, bold, white. |
| Current Task — progress bar | `4dp` tall, track `#252525`, fill `#22C55E` (green). |
| Today's Limits — row | Icon `40dp`, app name `15sp` white, **terse** time on the right: `13sp`, color `#8B92A5`, right-aligned. Terse format only — see formatter below. |
| Bottom icons | `56dp` circles, fill `#1E1E1E`, no blur, no border needed (solid fill reads fine against flat black). |

**Terse allowance formatter** (new small helper, used only by Classic):

```kotlin
private fun formatUsedTerse(card: AllowanceCardData): String = when (card.mode) {
    "count" -> "${card.used}/${card.total}"
    else    -> formatRemainingMs(card.used).let { if (it == "0m") "<1m" else it }  // shows USED, not remaining
}
```

Classic shows one number (`"42m"`, matching the reference image exactly) —
resist the urge to add "used" or "left" text here; that's the whole point
of the terse format per your own rationale ("scan in 1 sec").

---

## §5  Glassy theme — exact spec

Applies when `launcherTheme === 'glassy'`.

| Element | Spec |
|---|---|
| Background | User's wallpaper (`launcherWallpaperUri` or system wallpaper) — reuse the existing `loadCustomWallpaper()` / `applyWallpaperTint()` methods unchanged. |
| Clock | Same `72sp` size, but rendered with the existing `applyWallpaperTint()`-derived accent rather than pure white if you want the wallpaper-color-adaptive feel from the original plan — otherwise plain white is fine too; this is a taste call, not a functional one. |
| Status pill | Reuse the existing frosted-glass pill styling already in the current `buildProductivityStrip`'s chip background (`GLASS_LIGHT`/`GLASS_MID` tokens) rather than defining new ones. |
| Cards | Reuse the existing `GLASS_MID` / `GLASS_BORDER_BRIGHT` frosted card styling already defined in the current code's design tokens — this theme is exactly what those tokens were built for; no new values needed. |
| Today's Limits — row | Icon `40dp` (same as Classic), but time column shows the **full** sentence: `"{used} used • {remaining} left"`. |
| Bottom icons | Two small **individual** frosted circles (reuse the existing per-icon frosted-circle background), each sized to its touch target only — explicitly **not** wrapped in one shared pill/bar. This is the direct fix described in your reference material: kill the grouped capsule, let the wallpaper show through between the two icons. |

**Full allowance formatter** (new helper, used only by Glassy):

```kotlin
private fun formatUsedFull(card: AllowanceCardData): String = when (card.mode) {
    "count" -> {
        val remaining = card.total - card.used
        if (remaining <= 0) "no opens left" else "$remaining of ${card.total} opens left"
    }
    else -> "${formatRemainingMs(card.used)} used • ${formatRemainingMs(card.total - card.used)} left"
}
```

`AllowanceCardData` already carries `used`/`total` as raw longs — this
formatter is new, but no new data collection is needed; it's purely a
presentation change over data that's already there.

---

## §6  App drawer — shared: functional filter chips

Both drawer styles share the same four chips and the same filtering logic —
build this once, use it in both.

```kotlin
enum class DrawerFilter { ALL, FOCUS_TOOLS, LIMITED_TODAY, BLOCKED }

private fun filterDrawerApps(all: List<DrawerItem.App>, filter: DrawerFilter): List<DrawerItem.App> {
    return when (filter) {
        DrawerFilter.ALL -> all
        DrawerFilter.FOCUS_TOOLS -> {
            val focusTools = parseJsonArray(prefs.getString("focus_tool_packages", "[]") ?: "[]").toSet()
            all.filter { it.packageName in focusTools }
        }
        DrawerFilter.LIMITED_TODAY -> {
            val limited = loadAllowanceCardData().map { it.packageName }.toSet()
            all.filter { it.packageName in limited }
        }
        DrawerFilter.BLOCKED -> {
            val blocked = getBlockedPackages()   // existing method, unchanged
            all.filter { it.packageName in blocked }
        }
    }
}
```

Chip row: `ALL APPS` / `FOCUS TOOLS` / `LIMITED TODAY` / `BLOCKED ({count})`
— count comes from `getBlockedPackages().size`, computed once when the
drawer opens. Active chip styling: filled background for the active one
(teal `#2DD4BF` for Classic per your spec; the existing `ACCENT`/`ACCENT_TEXT`
tokens for Glassy), muted/outlined for the rest.

This is genuinely new filtering logic — the current drawer has sections
(from the original plan) but no chip-based filtering. Both drawer styles
below call `filterDrawerApps()` before building their item list.

---

## §7  App drawer — Classic List

Solid `#0E0E0E` background (same flat black as the Classic home screen —
no separate "drawer background" color to maintain).

| Element | Spec |
|---|---|
| Row height | `52dp` |
| Icon | `36dp` |
| App name | Visible, standard list-row typography (white text) — the reference **text** said "no title," but the reference **image** clearly shows app names next to every icon; following the image per your own instruction to prioritize it. |
| Search bar | Thin pill, `44dp` tall, `13sp` hint text `"Search apps"`, `1px` border, no blur (matches the flat-everywhere Classic philosophy). |
| Chip row | Same four chips as §6, teal `#2DD4BF` active state. |

**Editing — deliberately minimal, not the Glassy edit mode.** Long-press
opens a plain `AlertDialog` (same pattern as the current `showDrawerIconMenu`
— extend that existing method rather than building new UI):

```kotlin
private fun showDrawerIconMenu(pkg: String, label: String) {
    val hidden = parseJsonArray(prefs.getString("drawer_hidden_packages", "[]") ?: "[]").toSet()
    val isHidden = pkg in hidden
    AlertDialog.Builder(this)
        .setTitle(label)
        .setItems(arrayOf(
            if (isHidden) "Unhide from Drawer" else "Hide from Drawer",
            "App Info",
        )) { _, which ->
            when (which) {
                0 -> toggleDrawerHidden(pkg)
                1 -> openAppInfo(pkg)
            }
        }
        .create()
        .show()
}
```

No resize, no drag-to-reorder, no grid-size setting — none of those concepts
apply to a list, and Classic's whole premise is density and speed, not
customization depth. `"Lock app"` (from Glassy's edit sheet) doesn't need a
separate entry here either — "blocked" state is already visually shown via
dimming (existing pattern, same as home grid icons) and isn't something you
toggle from the drawer; it's controlled from Defense settings.

`drawer_hidden_packages` is a new, simple SharedPrefs key — a JSON array of
package names to exclude from `loadDrawerApps()`. **This set is shared with
Glassy** (§8) — hiding an app from one drawer style hides it from both,
since it's the same underlying app library, just displayed differently.

---

## §8  App drawer — Glassy Grid + dedicated Edit Mode

Wallpaper/nebula background (reuse the existing `applyWallpaperTint()` +
frosted-glass tokens already in the drawer's current implementation).

| Element | Spec |
|---|---|
| Grid | 4 columns default (`4x5` visible rows without scrolling on a typical screen), user-adjustable to `5x6` (see Edit Mode below). |
| Icon | `42dp` (down from the current `56dp` — this is the density refinement from your reference material). |
| Search bar | `44dp` tall (down from `64dp`), `13sp` hint `"Search FocusFlow"`, `1px` teal stroke, glass blur kept (Glassy is the theme that keeps blur). |
| Gaps | Search-to-chips `12dp` (was `32dp`), chips-to-first-row `16dp` (was `28dp`), icon vertical gap `12dp` (was `24dp`) — apply directly to the current drawer's existing margin constants. |
| Chip row | Same four chips as §6, existing `ACCENT`/`ACCENT_TEXT` active-state tokens. |

### Edit Mode — new dedicated screen state, not a per-icon dialog

This is the one piece that's genuinely new UI, matching the reference image
precisely: a full-drawer edit state entered via a "Customize" action (e.g.
long-press on empty drawer space, or an edit icon in the drawer header — your
call on the trigger, both are common patterns).

**Header, while in edit mode:** replaces the normal drawer header with
`"Edit Mode"` on the left, `Cancel` / `Done` buttons on the right. `Cancel`
discards any changes made this session (size, hidden, locked, order);
`Done` persists them.

**Each icon, while in edit mode:** small drag-handle badge in the top-right
corner (reuse a simple `≡` glyph in a small circular badge, `18dp`, muted
background) — this badge is the drag affordance; the rest of the icon
remains tappable for nothing (taps do nothing while in edit mode; only drag
and the bottom sheet's controls are active).

**Bottom sheet — "Customize Drawer":**

```
Icon Size          [ 100% ]
80% ─────●───────── 120%

[  4×5  ]  [  5×6  ]        ← Grid Size, single-select

[ Hide app ]  [ Lock app ]  ← apply to whichever icon is currently focused/dragged
```

- Icon Size slider: `80%`–`120%`, persisted as `drawer_icon_scale: Float` in
  SharedPrefs, applied as a multiplier on the base `42dp` icon size.
- Grid Size: `4x5` / `5x6`, persisted as `drawer_grid_columns: Int` (4 or 5).
  Recomputing column count on the fly is a `GridLayout.setColumnCount()`
  call — no need to rebuild the whole drawer view.
- Hide app: writes to the **same** `drawer_hidden_packages` key from §7 —
  shared between both drawer styles.
- Lock app: this is the same "blocked" concept as elsewhere in the app —
  clicking it should route to the existing Defense/Always-On flow rather
  than maintaining a separate lock list here. Simplest correct behavior:
  treat "Lock app" in this sheet as a shortcut that opens the Always-On app
  picker pre-scrolled to this package, rather than building a second,
  parallel "locked" concept that would need to be reconciled with the real
  blocking system.

**Drag-to-reorder:** persist final order as `drawer_custom_order: List<String>`
(package names, in order) — when present, `loadDrawerApps()` sorts by this
list first, falling back to alphabetical for any package not yet in it (newly
installed apps land at the end until the user drags them, rather than
disappearing or crashing on a missing sort key).

---

## Validation checklist

**Settings & config screen:**
- [ ] `launcherTheme: 'classic' | 'glassy'` added to `AppSettings` + `DEFAULT_SETTINGS`
- [ ] `focusToolPackages: string[]` added to `AppSettings` + `DEFAULT_SETTINGS` (empty default)
- [ ] Both synced to native via the same `updateSettings` pattern as `launcherClockStyle`
- [ ] `home-launcher.tsx`: Clock style toggle removed
- [ ] `home-launcher.tsx`: Dock order section removed
- [ ] `home-launcher.tsx`: Home grid order section removed
- [ ] `home-launcher.tsx`: old single `<LauncherPreview>` replaced with side-by-side `ThemePreviewCard` pair
- [ ] Wallpaper picker row only shown when `launcherTheme === 'glassy'`
- [ ] Tapping a `ThemePreviewCard` updates `launcherTheme` immediately, no separate Apply step
- [ ] "Set as Default" hide-when-already-default: confirmed already working, untouched
- [ ] Defense-tab entry point to Configure Home Launcher: confirmed already working, untouched

**Home screen — shared:**
- [ ] `buildDockArea`, `refreshHomeGrid`, `addHomeGridIcon`, `refreshDock`, `addDockIcon` deleted
- [ ] `showHomeIconMenu`, `showDockIconMenu`, `addToHome`, `removeFromHome`, `addToDock`, `removeFromDock` deleted
- [ ] `buildProductivityStrip`, `buildChip`, `refreshProductivityStrip` deleted — replaced by `buildStatusPill()`
- [ ] `buildAllowanceStrip`, `buildAllowanceCard` deleted as standalone methods — logic absorbed into `buildTodaysLimits()`
- [ ] `loadAllowanceCardData()` itself is unchanged — reused as-is
- [ ] `buildFocusSessionCard()`/`refreshFocusCard()` reused unchanged, restyled only
- [ ] `buildTwoIconRow()` renders two independent circular icons — no shared pill/bar container
- [ ] `buildHomeLayout()` column order matches §3 exactly, no grid anywhere

**Classic theme:**
- [ ] Background flat `#0E0E0E`, no wallpaper read at all when this theme is active
- [ ] Clock `72sp`, no stroke, no blur, no shadow
- [ ] All cards use flat fills — zero `blur`/`RenderEffect` calls anywhere in the Classic render path
- [ ] Today's Limits uses `formatUsedTerse()` — single short value, no "used"/"left" wording
- [ ] Bottom icons are solid `#1E1E1E` fills, `56dp`

**Glassy theme:**
- [ ] Background reads wallpaper via existing `loadCustomWallpaper()`/`applyWallpaperTint()`
- [ ] Cards reuse existing `GLASS_MID`/`GLASS_BORDER_BRIGHT` tokens — no new frosted-glass values invented
- [ ] Today's Limits uses `formatUsedFull()` — full "used • left" sentence
- [ ] Bottom icons are two independent frosted circles — confirmed no shared capsule/bar

**Drawer — shared:**
- [ ] `DrawerFilter` enum + `filterDrawerApps()` implemented once, used by both drawer styles
- [ ] `BLOCKED` filter reuses existing `getBlockedPackages()` — no new blocked-set logic
- [ ] `LIMITED_TODAY` filter reuses existing `loadAllowanceCardData()`
- [ ] `FOCUS_TOOLS` filter reads the new `focus_tool_packages` SharedPrefs key
- [ ] `drawer_hidden_packages` key is shared between both drawer styles

**Drawer — Classic List:**
- [ ] Row `52dp`, icon `36dp`, app name visible (not hidden, per image over text)
- [ ] Search bar `44dp`, no blur
- [ ] Long-press menu extended with Hide/Unhide — no resize, no grid-size, no drag-reorder
- [ ] Active chip color is teal `#2DD4BF`

**Drawer — Glassy Grid:**
- [ ] Icon `42dp` (not the old `56dp`), search `44dp` (not the old `64dp`)
- [ ] Gaps updated: search→chips `12dp`, chips→row `16dp`, icon vertical `12dp`
- [ ] Edit Mode header (Cancel/Done) implemented as a distinct mode, not a per-icon popup
- [ ] Drag-handle badge appears on every icon while in Edit Mode
- [ ] Customize Drawer sheet: Icon Size slider (`drawer_icon_scale`), Grid Size 4×5/5×6 (`drawer_grid_columns`)
- [ ] "Hide app" in the sheet writes to the same shared `drawer_hidden_packages` key
- [ ] "Lock app" opens the existing Always-On picker rather than creating a second lock concept
- [ ] Drag-to-reorder persists to `drawer_custom_order`; new/un-ordered apps fall back to alphabetical, don't disappear

---

## Priority order

1. **§1 + §3** — settings fields and the shared home-layout teardown/reorder. Nothing else can be tested until the old grid/dock is actually gone and the new column order exists.
2. **§4 — Classic home screen.** Simplest to verify correctly (no blur, no wallpaper interaction) — get this right first as the reference for "does the new structure work at all."
3. **§5 — Glassy home screen.** Mostly reusing existing tokens; should be fast once §4 proves the structural layout.
4. **§2 — Config screen overhaul.** Needs §1's setting to exist first; do this once both home themes actually render, so the mini-previews have something real to represent.
5. **§6 + §7 — Classic drawer + shared chip filtering.** Get the shared filtering logic right here first, since Glassy's drawer (§8) reuses it directly.
6. **§8 — Glassy drawer + Edit Mode.** Most implementation surface of any single section — do this last, once the shared filtering and the simpler Classic editing pattern are proven.
