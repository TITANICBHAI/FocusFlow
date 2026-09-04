# FocusFlow Launcher — Full Premium Redesign Plan (v2)

> Agent execution plan. Do not implement anything not listed here.
> All changes are in `LauncherActivity.kt` unless a path is stated explicitly.

> **Revision note (human — not an implementation instruction):** v2 fixes all
> accessibility findings from the design review. Changed sections carry a `[REVISED]`
> tag. New section §0 adds two private helpers required by §§5 7 8 9 11 12 — implement
> it first, alongside §1. Summary of what changed: §0 new; §1 ACCENT_TEXT token added +
> TEXT_MUTED alpha corrected; §3 search bar repositioned to bottom; §4 clock two-tone
> inverted (minutes bright); §5 animationsEnabled guard added; §6 chip height 44 dp;
> §7 press scale 0.93 f / overshoot 1.5 f / blocked contentDescription /
> animationsEnabled guard; §8 same scale fixes + contentDescriptions on all circle
> buttons; §9c section headers use ACCENT_TEXT; §9e–9f animationsEnabled guards; §11
> contrast fallback in tint; §12 animationsEnabled guard on fling; §13 sixteen new
> checklist items.

---

## Research basis

Studied before writing this plan:

- **Mako** (rama-io, native Kotlin, GPL-3) — sharp-angle typography, app groups,
  collapsible sections, bottom search bar, Dracula theme; proves that "minimal" and
  "beautiful" are not opposites. Key takeaway: one coherent font-size ladder does more
  than any graphic treatment.
- **Niagara Launcher** — no icon grid at all; vertical alphabetical app list; favourites
  on home; one-handed ergonomics; adaptive app ranking by usage. Key takeaway: the
  launcher can have an opinion about *how* you use apps, not just *which* apps you see.
- **Pixel Launcher / iOS** — "At a Glance" widget at the top, predictive dock,
  wallpaper-extracted colour, spring physics on icon press, sub-100 ms touch response.
- **Nova / Lawnchair** — icon pack support, per-item resize, gesture mapping.
- `androidx.dynamicanimation` — `SpringAnimation` + `FlingAnimation` for physics-based
  motion without writing Runge-Kutta by hand.

---

## Design language: "Focused Glass"

FocusFlow is a *productivity enforcement* app. Its launcher must communicate:

1. **What you are supposed to be doing right now** (session card, always visible when
   active)
2. **What is blocked and why** (dimmed icon + badge, never hidden)
3. **Premium calm** (deep dark glass, indigo accent, breathing room, no clutter)

Reference aesthetic: iOS 18 lock screen + Niagara typography precision + Material You
depth. Not flat-minimal (feels cheap). Not material-heavy (feels corporate).
Focused Glass.

The search bar is intentionally placed at the **bottom** of the home screen, above the
dock. This reduces reflexive distraction-app-searching while keeping the feature
accessible. On a focus-enforcement launcher, the path to the full app drawer should
carry a small amount of friction — not invisible, just not the first thing the eye
lands on.

---

## 0  Accessibility helpers [NEW]

Add these two `private` methods to the `LauncherActivity` class body near the top of
the helper-methods block (before `dp()`). They are called by §§5 7 8 9 11 12.

### 0a  `animationsEnabled()`

Returns `false` when the user has set Animator Duration Scale to 0 in Developer Options
(Android's system-level reduce-motion control). Every animation block in this plan must
be wrapped with this guard.

```kotlin
private fun animationsEnabled(): Boolean {
    val scale = android.provider.Settings.Global.getFloat(
        contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return scale > 0f
}
```

### 0b  `contrastRatio()`

Used in §11 to validate that a dynamically computed accent colour still meets WCAG AA
for large text (≥ 3.0 : 1) before applying it. Uses the IEC 61966-2-1 linearisation
formula. No extra imports required — uses `java.lang.Math`.

```kotlin
private fun contrastRatio(fg: Int, bg: Int): Double {
    fun linearize(c: Double): Double =
        if (c <= 0.04045) c / 12.92
        else Math.pow((c + 0.055) / 1.055, 2.2)
    fun luminance(color: Int): Double {
        val r = linearize(Color.red(color)   / 255.0)
        val g = linearize(Color.green(color) / 255.0)
        val b = linearize(Color.blue(color)  / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    val l1      = luminance(fg)
    val l2      = luminance(bg)
    val lighter = maxOf(l1, l2)
    val darker  = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}
```

---

## 1  Design token constants [REVISED]

Replace the entire `companion object` colour block. Delete `DOCK_SURFACE`. Add
everything below exactly as written.

```kotlin
companion object {

    // ── Typography scale ─────────────────────────────────────────────────
    private const val SIZE_CLOCK_MAIN  = 86f
    private const val SIZE_CLOCK_AMPM  = 18f
    private const val SIZE_DATE        = 13f
    private const val SIZE_LABEL_HOME  = 11.5f
    private const val SIZE_LABEL_DOCK  = 10.5f
    private const val SIZE_SEARCH_HINT = 14f
    private const val SIZE_SECTION_HDR = 11f

    // ── Spacing unit — 8-point grid ──────────────────────────────────────
    // Every margin and padding in this file is a multiple of UNIT.
    private const val UNIT = 8  // dp

    // ── Icon sizes ───────────────────────────────────────────────────────
    private const val ICON_HOME        = 54   // dp — actual drawable inside cell
    private const val ICON_DOCK        = 52   // dp
    private const val ICON_CELL_FRAME  = 62   // dp — cell frame incl. backdrop
    private const val ICON_DOCK_FRAME  = 60   // dp

    // ── Glass surfaces ───────────────────────────────────────────────────
    private val GLASS_ULTRA        = Color.parseColor("#0FFFFFFF")
    private val GLASS_LIGHT        = Color.parseColor("#1AFFFFFF")
    private val GLASS_MID          = Color.parseColor("#2AFFFFFF")
    private val GLASS_HEAVY        = Color.parseColor("#3CFFFFFF")
    private val GLASS_BORDER       = Color.parseColor("#20FFFFFF")
    private val GLASS_BORDER_BRIGHT = Color.parseColor("#35FFFFFF")

    // ── Brand ────────────────────────────────────────────────────────────
    // ACCENT (#6366f1, indigo-500) is 4.25:1 on DRAWER_BG — sufficient for
    // large text (≥ 18 sp bold) but fails WCAG AA for normal / small text.
    // Use ACCENT_TEXT (#818CF8, indigo-400) for any text under 18 sp that
    // needs an accent colour. ACCENT stays for non-text uses only.
    private val ACCENT         = Color.parseColor("#6366f1") // borders, icons, fills
    private val ACCENT_TEXT    = Color.parseColor("#818CF8") // 6.25:1 on #111827 ✓ WCAG AA
    private val ACCENT_DIM     = Color.parseColor("#406366f1")
    private val ACCENT_GLOW    = Color.parseColor("#1A6366f1")
    private val ACCENT_SURFACE = Color.parseColor("#226366f1")

    // ── Text ─────────────────────────────────────────────────────────────
    private val TEXT_PRIMARY = Color.WHITE
    private val TEXT_DIM     = Color.parseColor("#CCF0F4FF") // 80% white, blue tint
    // TEXT_MUTED alpha raised from #80 (50%) to #B3 (70%).
    // Composited on #111827: effective ~#7C889B → contrast 5.2:1 ✓ WCAG AA
    private val TEXT_MUTED   = Color.parseColor("#B3AAB8CC")
    // TEXT_BLOCKED is intentionally low-contrast (33% white) — it signals
    // "blocked" without completely hiding the icon. Keep as-is.
    private val TEXT_BLOCKED = Color.parseColor("#55FFFFFF")

    // ── Status ───────────────────────────────────────────────────────────
    private val RED_BLOCK     = Color.parseColor("#EF4444")
    private val RED_BLOCK_DIM = Color.parseColor("#99EF4444")
    private val AMBER_WARN    = Color.parseColor("#F59E0B")

    // ── Scrim ─────────────────────────────────────────────────────────────
    private val SCRIM_FULL_TOP = Color.parseColor("#26000000") // 15%
    private val SCRIM_FULL_BTM = Color.parseColor("#BF000000") // 75%
    private val SCRIM_DOCK_BTM = Color.parseColor("#F0000000") // 94%

    private val DRAWER_BG = Color.parseColor("#F0111827")
}
```

Add the following instance-variable fields alongside the existing ones:

```kotlin
private var minuteView:       TextView?     = null
private var colonView:        TextView?     = null
private var wallpaperAccent:  Int           = ACCENT
private var productivityStrip: LinearLayout? = null
```

---

## 2  Background gradient — three-layer depth

In `buildHomeLayout()`, replace the single `bottomGrad` view with **three** layered
`View`s, all added to `rootFrame` in this order after `scrim`:

| Layer | Height | Orientation | Colors | Purpose |
|---|---|---|---|---|
| `gradFull` | `MATCH_PARENT` | TOP_BOTTOM | `SCRIM_FULL_TOP → SCRIM_FULL_BTM` | Screen-wide depth |
| `gradDock` | `240dp`, gravity=BOTTOM | TOP_BOTTOM | `TRANSPARENT → SCRIM_DOCK_BTM` | Dock legibility |
| `gradTop` | `180dp`, gravity=TOP | BOTTOM_TOP | `#44000000 → TRANSPARENT` | Status-bar legibility |

All three use `GradientDrawable`. `gradTop` reverses direction (BOTTOM_TOP) so the
dark part is at the top edge. Set `layoutParams.gravity` explicitly for `gradDock` and
`gradTop`.

---

## 3  Search bar [REVISED]

Build `private fun buildSearchBar(): View`. Insert its result as the **penultimate
child** of the root column, directly above the call to `buildDockArea()`.

The root column order in `buildHomeLayout()` must be exactly:

```
buildClockWidget()
buildFocusSessionCard()
buildProductivityStrip()
buildAllowanceStrip()
home grid (spacer + GridLayout)
buildSearchBar()      ← above dock, NOT above clock
buildDockArea()
```

### Layout spec

```
height:  48 dp
margins: UNIT*2 left/right (16 dp), UNIT*2 top (16 dp), UNIT bottom (8 dp)
background: GradientDrawable
  shape:        RECTANGLE
  cornerRadius: 24 dp
  fill:         GLASS_MID
  stroke:       1 dp  GLASS_BORDER_BRIGHT
padding: 18 dp left, 18 dp right, 0 top/bottom
orientation: HORIZONTAL, CENTER_VERTICAL
contentDescription: "Search apps — opens app drawer"
```

### Contents

**Search icon** — custom `View`, 20×20 dp:

- `onDraw`: draw a circle (radius 6 dp, stroke 2 dp, color `TEXT_MUTED`) then a line
  from the 4 o'clock position outward (length 5 dp, same stroke and color).
  Anti-alias everything.
- `contentDescription = "Search"` — set on this view directly.

**Hint text** — `TextView`, `weight = 1f`:

```
text:          "Search apps"
textSize:      SIZE_SEARCH_HINT
color:         TEXT_MUTED
letterSpacing: 0.01f
leftMargin:    12 dp
```

**Mic icon** — custom `View`, 20×20 dp (rightmost):

- `onDraw`: draw a rounded-rectangle mic body (8 dp tall, 5 dp wide, 2 dp corner) then
  a U-curve beneath it and a short vertical stem — all `TEXT_MUTED`, stroke 1.8 dp.
- `contentDescription = "Voice search"` — set on this view directly.

### Behaviour

Tap anywhere on the bar → `openDrawer()` then post a 150 ms runnable:
`drawerSearchInput?.requestFocus()` (the existing `EditText` reference inside the
drawer). The bar does **not** manage its own keyboard; it is a shortcut into the
existing drawer search field.

---

## 4  Clock widget [REVISED]

Replace `buildClockWidget()` entirely. Keep `refreshFocusCard()` and the allowance
strip refresh counter calls at the end of `updateClockText()` — unchanged.

### Structure

```
Outer wrap: VERTICAL LinearLayout, CENTER_HORIZONTAL
  topMargin:    UNIT*2 (16 dp)
  bottomMargin: UNIT   (8 dp)
```

**Date row** — `TextView` (`dateView`):

```
format:        "EEE, d MMM"  →  "Wed, 2 Sep"
textSize:      SIZE_DATE
color:         TEXT_MUTED
letterSpacing: 0.14f
typeface:      DEFAULT / NORMAL
bottomMargin:  10 dp
```

**Time row** — `LinearLayout`, HORIZONTAL, CENTER_VERTICAL, CENTER_HORIZONTAL:

In **12 h mode**, the row contains four children in this order:

| Field | View name | Size | Color | Notes |
|---|---|---|---|---|
| Hour digits | `clockView` | `SIZE_CLOCK_MAIN` | `TEXT_DIM` | BOLD — dimmer; hours carry less minute-precision information |
| Colon | `colonView` | `SIZE_CLOCK_MAIN * 0.85f` | `TEXT_DIM` | BOLD; `bottomMargin = 10 dp` for optical drop |
| Minute digits | `minuteView` | `SIZE_CLOCK_MAIN` | `TEXT_PRIMARY` | BOLD — bright; minutes are the granular unit for productivity awareness |
| AM/PM | `ampmView` | `SIZE_CLOCK_AMPM` | `ACCENT` | BOLD; `bottomMargin = 22 dp`; `leftMargin = 8 dp`. 18 sp bold = WCAG large text → ACCENT passes 3 : 1 ✓ |

In **24 h mode**: single `clockView` child only (`HH:mm` format), full `TEXT_PRIMARY`,
`colonView` / `minuteView` / `ampmView` set to `View.GONE`.

### `updateClockText()` implementation

```kotlin
val now = Date()
if (use24h) {
    clockView?.text        = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    colonView?.visibility  = View.GONE
    minuteView?.visibility = View.GONE
    ampmView?.visibility   = View.GONE
} else {
    colonView?.visibility  = View.VISIBLE
    minuteView?.visibility = View.VISIBLE
    ampmView?.visibility   = View.VISIBLE
    clockView?.text  = SimpleDateFormat("h",  Locale.getDefault()).format(now)
    colonView?.text  = ":"
    minuteView?.text = SimpleDateFormat("mm", Locale.getDefault()).format(now)
    ampmView?.text   = SimpleDateFormat("a",  Locale.getDefault()).format(now)
}
dateView?.text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now)
```

Retain the allowance strip refresh counter and the `refreshFocusCard()` call exactly
as they were in the previous version of this method.

---

## 5  Focus session card [REVISED — animation guard]

Replace `buildFocusSessionCard()`. Keep `refreshFocusCard()` logic **completely
unchanged** — only the container's visual structure and its pressed-state animation
change.

### New visual spec

```
orientation: HORIZONTAL
gravity:     CENTER_VERTICAL
height:      WRAP_CONTENT
margins:     UNIT*2.5 left/right (20 dp), UNIT top, UNIT bottom
padding:     16 dp left/right, 14 dp top/bottom
visibility:  GONE  — refreshFocusCard() handles toggling
```

**Background** — `LayerDrawable` with two layers:

- Layer 0: `GradientDrawable` rectangle, `18 dp` corners, gradient LEFT_RIGHT,
  colors `[#1A6366f1, #0A000000]` — brand fade
- Layer 1: `GradientDrawable` rectangle, `18 dp` corners, stroke only
  `1.5 dp #4D6366f1` — border on top of gradient

```kotlin
card.background = LayerDrawable(arrayOf(layer0, layer1))
```

**Left — icon frame** (`FrameLayout`, 40×40 dp):

- Background: `GradientDrawable` oval, fill `ACCENT_SURFACE`, stroke `1 dp ACCENT_DIM`
- Child `iconView` (`TextView`): emoji, `17 sp`, centered

**Middle — label column** (`LinearLayout`, VERTICAL, `weight = 1f`,
`paddingLeft = 12 dp`):

- `titleView`: `14 sp`, `TEXT_PRIMARY`, `Typeface.BOLD`, 1 line, `END` ellipsis
- `subtitleView`: `12 sp`, `TEXT_MUTED`, 1 line, `END` ellipsis, `topMargin = 3 dp`

**Right — chevron** (`TextView`):

```
text:     "›"
textSize: 26 sp
color:    ACCENT
```

**Tap**: `setOnClickListener { openFocusFlow() }` — unchanged.

### Pressed-state animation

After `setOnClickListener`, add:

```kotlin
card.setOnTouchListener { v, ev ->
    when (ev.action) {
        MotionEvent.ACTION_DOWN ->
            if (animationsEnabled())
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
                    .setInterpolator(DecelerateInterpolator()).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (animationsEnabled())
                v.animate().scaleX(1f).scaleY(1f).setDuration(150)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            else { v.scaleX = 1f; v.scaleY = 1f }
        }
    }
    false  // let click listener fire
}
```

---

## 6  FocusFlow productivity strip [REVISED — chip height]

New horizontal mini-widget showing at-a-glance stats. Sits between the focus card and
the allowance strip, visible only when there is data to show.

Build `private fun buildProductivityStrip(): LinearLayout` and insert it in
`buildHomeLayout()` between `buildFocusSessionCard()` and `buildAllowanceStrip()`.

Add `private var productivityStrip: LinearLayout? = null` to instance variables
(already listed in §1).

### Layout

```
orientation: HORIZONTAL
margins:     UNIT*2.5 left/right (20 dp), 4 dp top/bottom
gravity:     CENTER_VERTICAL
visibility:  GONE  — refreshProductivityStrip() handles toggling
```

### Chips

Up to **3** chips in a horizontal `LinearLayout`. Each chip:

```
background: GradientDrawable, 12 dp corner, fill GLASS_LIGHT, stroke 1 dp GLASS_BORDER
height:     44 dp    ← WCAG 2.5.5 minimum touch target — do not reduce
padding:    10 dp left/right
rightMargin: 6 dp
contents:   [icon TextView 13 sp] [5 dp gap] [label TextView 12 sp TEXT_DIM]
```

The three chips, left-to-right:

| Condition | Icon | Label |
|---|---|---|
| Standalone block active | 🔒 | `"N blocked"` — count from `PREF_SA_PKGS` |
| Always-On active | 🛡️ | `"Always-On"` |
| Focus session with next task | ⏭ | `"Next: <next_task_name>"` — reads `"next_task_name"` pref |

Show only chips whose condition is met. Hide the strip entirely when zero chips apply.

### `refreshProductivityStrip()`

```kotlin
private fun refreshProductivityStrip() {
    val strip = productivityStrip ?: return
    strip.removeAllViews()
    val chips = mutableListOf<View>()

    val saActive     = prefs.getBoolean(PREF_SA_ACTIVE, false)
    val alwaysActive = prefs.getBoolean(PREF_ALWAYS_BLOCK, false)
    val nextTask     = prefs.getString("next_task_name", null)?.takeIf { it.isNotBlank() }

    if (saActive) {
        val count = parseJsonArray(prefs.getString(PREF_SA_PKGS, "[]") ?: "[]").size
        if (count > 0) chips += buildChip("🔒", "$count blocked")
    }
    if (alwaysActive) chips += buildChip("🛡️", "Always-On")
    if (nextTask != null) chips += buildChip("⏭", "Next: $nextTask")

    chips.forEach { strip.addView(it) }
    strip.visibility = if (chips.isEmpty()) View.GONE else View.VISIBLE
}
```

`private fun buildChip(icon: String, label: String): View` constructs the chip as
described above. Chip height must be exactly **44 dp** — do not reduce.

Call `refreshProductivityStrip()` from:
- `onResume()`
- `updateClockText()` (same 60-tick cadence as allowance strip refresh)
- `preferenceListener` for `PREF_SA_ACTIVE`, `PREF_SA_PKGS`, `PREF_ALWAYS_BLOCK`,
  `"next_task_name"`

---

## 7  Home grid icon cells [REVISED]

In `addHomeGridIcon()`:

### Cell wrapper

```
margins: 6 dp top/bottom, 3 dp left/right
```

### Icon backdrop

Wrap `iconView` in a `FrameLayout` (`ICON_CELL_FRAME × ICON_CELL_FRAME` = 62×62 dp):

```kotlin
val backdrop = View(this).apply {
    background = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(GLASS_ULTRA)
    }
    layoutParams = FrameLayout.LayoutParams(
        dp(ICON_CELL_FRAME), dp(ICON_CELL_FRAME)
    ).also { it.gravity = Gravity.CENTER }
}
iconFrame.addView(backdrop)   // add BEFORE iconView so icon sits on top
```

### Blocked state

When `isBlocked`:

```kotlin
iconView.alpha       = 0.30f
backdrop.background  = GradientDrawable().apply {
    shape        = GradientDrawable.RECTANGLE
    cornerRadius = dp(16).toFloat()
    setColor(Color.parseColor("#1AEF4444"))   // faint red tint on the cell
}
// Block badge: 12 dp circle, stroke 1.5 dp #BB000000, fill RED_BLOCK
item.contentDescription = "${label}, blocked"
```

When not blocked:

```kotlin
item.contentDescription = label
```

### Label

```kotlin
textSize      = SIZE_LABEL_HOME
color         = if (isBlocked) TEXT_BLOCKED else TEXT_DIM
setShadowLayer(5f, 0f, 2f, Color.parseColor("#BB000000"))
letterSpacing = 0.01f
topMargin     = 6 dp
```

### Press animation [REVISED]

Add after the existing `setOnClickListener` / `setOnLongClickListener`:

```kotlin
item.setOnTouchListener { v, ev ->
    when (ev.action) {
        MotionEvent.ACTION_DOWN ->
            if (animationsEnabled())
                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100)
                    .setInterpolator(DecelerateInterpolator()).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (animationsEnabled())
                v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            else { v.scaleX = 1f; v.scaleY = 1f }
        }
    }
    false
}
```

Apply the **identical** animation block (same scale values, same guard) inside
`addDockIcon()` for dock icon items.

---

## 8  Dock area [REVISED]

Replace `buildDockArea()` entirely.

### 8a  Quick-action row — circle buttons [REVISED]

```
orientation: HORIZONTAL
gravity:     CENTER
margins:     UNIT*4 left/right (32 dp), UNIT*1.5 top, UNIT bottom
```

Two groups, each a `LinearLayout` (`VERTICAL`, `CENTER`, `weight = 1f`).

**Circle button** — custom `View`, 58×58 dp:

All Apps circle:
- Background: `GradientDrawable` oval, fill `GLASS_MID`, stroke `1.5 dp GLASS_BORDER_BRIGHT`
- `onDraw`: 3×3 dot grid (dot radius 2.5 dp, gap 6 dp, color `TEXT_PRIMARY`)
- `contentDescription = "All apps"` — set directly on this view

FocusFlow circle:
- Background: `GradientDrawable` oval, fill `ACCENT_SURFACE`, stroke `1.5 dp ACCENT_DIM`
- `onDraw`: letter **"F"** using `Paint`:
  ```
  textSize    = dp(22).toFloat()
  color       = ACCENT
  typeface    = Typeface.DEFAULT_BOLD
  textAlign   = Paint.Align.CENTER
  ```
  Center vertically via `Paint.getTextBounds`.
- `contentDescription = "Open FocusFlow"` — set directly on this view

**Label** — `TextView` below each circle:
```
textSize:  11 sp
color:     TEXT_MUTED
topMargin: 6 dp
```

Text: "All Apps" / "FocusFlow".

**Press animation** — same pattern as §7 (0.93 f scale, OvershootInterpolator(1.5f),
`animationsEnabled()` guard). Apply to the `LinearLayout` wrapper (circle + label),
not the inner `View` alone, so the touch area covers the label:

```kotlin
group.setOnTouchListener { v, ev ->
    when (ev.action) {
        MotionEvent.ACTION_DOWN ->
            if (animationsEnabled())
                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(100)
                    .setInterpolator(DecelerateInterpolator()).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (animationsEnabled())
                v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                    .setInterpolator(OvershootInterpolator(1.5f)).start()
            else { v.scaleX = 1f; v.scaleY = 1f }
        }
    }
    false
}
```

`setOnClickListener`: All Apps → `openDrawer()`, FocusFlow → `openFocusFlow()`.

### 8b  Dock pill

```kotlin
val dockCard = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity     = Gravity.CENTER
    background  = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(36).toFloat()   // rounder: 36 dp, up from 28 dp
        setColor(GLASS_MID)
        setStroke(dp(1), GLASS_BORDER_BRIGHT)
    }
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(100)   // taller: 100 dp, up from 90 dp
    ).also {
        it.setMargins(dp(UNIT), dp(UNIT / 2), dp(UNIT), dp(UNIT * 3))
    }
    setPadding(dp(UNIT), 0, dp(UNIT), 0)
    elevation = dp(8).toFloat()
}
```

### 8c  Dock icon cells [REVISED — blocked contentDescription]

In `addDockIcon()`, wrap each icon the same way as home grid icons (§7) but with:

- Frame `ICON_DOCK_FRAME × ICON_DOCK_FRAME` (60 dp)
- `cornerRadius = dp(14).toFloat()` — slightly less than home grid
- Blocked state: `alpha = 0.30f` + red badge — no red backdrop tint on dock icons
- `iconView.contentDescription = if (isBlocked) "${label}, blocked" else label`
- Label: `SIZE_LABEL_DOCK`, `TEXT_DIM`

---

## 9  App drawer [REVISED — section headers + animation guards]

All changes are inside `openDrawer()` and `DrawerAdapter`.

### 9a  Sheet background

```kotlin
sheet.setBackgroundColor(Color.parseColor("#F0111827"))
```

Add a frosted top edge inside `sheet` (before the search bar row):

```kotlin
val topBar = View(context).apply {
    background = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#40FFFFFF"), Color.TRANSPARENT)
    )
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
}
sheet.addView(topBar, 0)
```

### 9b  Drawer search bar

Find the existing `EditText`. Replace its background:

```kotlin
background = GradientDrawable().apply {
    shape        = GradientDrawable.RECTANGLE
    cornerRadius = dp(22).toFloat()
    setColor(GLASS_LIGHT)
    setStroke(dp(1), GLASS_BORDER)
}
textSize = 15f
setTextColor(TEXT_PRIMARY)
setHintTextColor(TEXT_MUTED)
hint = "Search apps…"
setPadding(dp(18), 0, dp(18), 0)
// height: 52 dp; margins: UNIT*2 all sides
```

### 9c  Section headers [REVISED — ACCENT_TEXT]

In `DrawerAdapter.onCreateViewHolder` for `DRAWER_TYPE_HEADER`:

```kotlin
textSize      = SIZE_SECTION_HDR
setTextColor(ACCENT_TEXT)        // ← was ACCENT. ACCENT_TEXT (#818CF8) = 6.25:1 on
                                  //   #111827 — passes WCAG AA for 11 sp bold ✓
typeface      = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
letterSpacing = 0.12f
setPadding(dp(16), dp(10), dp(16), dp(4))
```

### 9d  Drawer icon cells [REVISED — blocked contentDescription]

In `DrawerAdapter.onCreateViewHolder` for `DRAWER_TYPE_APP`, each cell is a
`LinearLayout` (`VERTICAL`, `CENTER`). Increase icon drawable to 52 dp and apply the
same backdrop treatment as §7 (12 dp corner `GradientDrawable`, fill `GLASS_ULTRA`).
Label: `11 sp`, `TEXT_DIM`.

In `DrawerAdapter.onBindViewHolder` for `DRAWER_TYPE_APP`:

```kotlin
appHolder.itemView.contentDescription =
    if (isBlocked) "${item.label}, blocked" else item.label
```

### 9e  Drawer open animation [REVISED — animationsEnabled guard]

After `rootFrame.addView(drawerOverlay)` and layout is built:

```kotlin
if (animationsEnabled()) {
    sheet.translationY = sheet.height.toFloat().coerceAtLeast(dp(600).toFloat())
    sheet.animate()
        .translationY(0f)
        .setDuration(380)
        .setInterpolator(DecelerateInterpolator(2.2f))
        .start()
    scrim.alpha = 0f
    scrim.animate().alpha(1f).setDuration(300).start()
} else {
    sheet.translationY = 0f
    scrim.alpha = 1f
}
```

### 9f  Drawer close animation [REVISED — animationsEnabled guard]

Set `isDrawerOpen = false` **immediately** (before the block below), so swipe
detection reacts at once.

```kotlin
if (animationsEnabled()) {
    val targetY = sheet.height.toFloat()
    sheet.animate()
        .translationY(targetY)
        .setDuration(280)
        .setInterpolator(AccelerateInterpolator(1.8f))
        .withEndAction { rootFrame.removeView(drawerOverlay); drawerOverlay = null }
        .start()
    scrim.animate().alpha(0f).setDuration(240).start()
} else {
    rootFrame.removeView(drawerOverlay)
    drawerOverlay = null
}
return
```

Do **not** call `rootFrame.removeView` outside this block — `withEndAction` owns
removal when animations are on; the else branch owns it when off.

---

## 10  Touch feedback — global tap ripple

For every tappable element (search bar, all circle buttons, all icon cells, focus card,
chips), set a ripple foreground:

```kotlin
private fun rippleForeground(cornerDp: Int = 16): Drawable {
    val mask = GradientDrawable().apply {
        setColor(Color.WHITE)
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = dp(cornerDp).toFloat()
    }
    return RippleDrawable(
        android.content.res.ColorStateList.valueOf(Color.parseColor("#30FFFFFF")),
        null,
        mask
    )
}
```

Call `view.foreground = rippleForeground()` after each `setOnClickListener`. For oval
items, pass `cornerDp = 999` to produce a circle mask. `RippleDrawable` is API 21+;
the project already targets ≥ 21.

---

## 11  Wallpaper tinting [REVISED — contrast fallback]

Pull the dominant wallpaper colour and blend it 40 % into `ACCENT` to tint the clock
and dock accent elements. Apply only to elements that use `ACCENT` in the clock and
dock — **not** to blocking-status indicators (those must stay constant).

```kotlin
private fun applyWallpaperTint() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
    try {
        val wm      = WallpaperManager.getInstance(this)
        val colors  = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return
        val dominant = colors.primaryColor.toArgb()

        val r = ((Color.red(dominant)   * 0.4f) + (Color.red(ACCENT)   * 0.6f)).toInt()
        val g = ((Color.green(dominant) * 0.4f) + (Color.green(ACCENT) * 0.6f)).toInt()
        val b = ((Color.blue(dominant)  * 0.4f) + (Color.blue(ACCENT)  * 0.6f)).toInt()
        val candidate = Color.rgb(r, g, b)

        // Contrast guard — some wallpaper palettes can push the blended colour
        // below 3.0:1 against the dark background. Fall back to base ACCENT if so.
        // Use the opaque equivalent of DRAWER_BG as the reference surface.
        val darkBg = Color.parseColor("#111827")
        wallpaperAccent = if (contrastRatio(candidate, darkBg) >= 3.0) candidate else ACCENT

    } catch (_: Exception) {
        wallpaperAccent = ACCENT
    }
}
```

`wallpaperAccent` is already declared in §1. Call `applyWallpaperTint()` from
`onCreate()` and `onResume()`.

---

## 12  Animation — drawer swipe physics [REVISED — animationsEnabled guard]

After the drawer opens, add fling-to-close physics inside the sheet's
`setOnTouchListener` (the existing close-gesture handler):

```kotlin
MotionEvent.ACTION_UP -> {
    velocityTracker?.computeCurrentVelocity(1000)
    val vy = velocityTracker?.yVelocity ?: 0f

    if (animationsEnabled() && vy > 800f) {
        val fling = FlingAnimation(sheet, DynamicAnimation.TRANSLATION_Y).apply {
            setStartVelocity(vy)
            setMinValue(0f)
            setMaxValue(dp(1200).toFloat())
            addEndListener { _, _, _, _ -> if (isDrawerOpen) closeDrawer() }
        }
        fling.start()
    } else {
        // Low velocity or animations disabled — snap decision on position
        if (sheet.translationY > dp(200) || vy > 800f) {
            closeDrawer()
        } else if (animationsEnabled()) {
            sheet.animate().translationY(0f).setDuration(200)
                .setInterpolator(DecelerateInterpolator(1.5f)).start()
        } else {
            sheet.translationY = 0f
        }
    }
}
```

Imports required:

```kotlin
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.DynamicAnimation
```

Add to `build.gradle` (app module) **if not already present**:

```groovy
implementation 'androidx.dynamicanimation:dynamicanimation:1.1.0'
```

---

## 13  Validation checklist [REVISED]

Run every item before marking the implementation done.

### Structural correctness

- [ ] `python3 -c "s=open('LauncherActivity.kt').read(); print(s.count('{') - s.count('}'))"` → **0**
- [ ] All new `private var` fields declared in the instance-variable block:
      `minuteView`, `colonView`, `wallpaperAccent`, `productivityStrip`
- [ ] `DOCK_SURFACE` not referenced anywhere (replaced by `GLASS_MID`)
- [ ] `rootFrame.setOnTouchListener` absent (removed in a prior session)
- [ ] `dispatchTouchEvent` override present and unchanged
- [ ] `buildFocusSessionCard()` returns `LinearLayout` and assigns `focusCard`
- [ ] `refreshFocusCard()` logic unchanged
- [ ] `openFocusFlow()` unchanged
- [ ] `buildAllowanceStrip()` / `refreshAllowanceStrip()` untouched
- [ ] `DrawerAdapter` inner class untouched except visual styling in `onCreateViewHolder`
      and `contentDescription` assignment in `onBindViewHolder`
- [ ] `openDrawer()` / `closeDrawer()` animation changes do not break `isDrawerOpen` flag
- [ ] `FlingAnimation` import resolves (gradle dependency added if missing)
- [ ] `RippleDrawable` applied only to tappable views, not passive views
- [ ] `applyWallpaperTint()` guarded behind `Build.VERSION_CODES.O_MR1` check
- [ ] `refreshProductivityStrip()` called from `onResume()`, `updateClockText()`,
      and `preferenceListener`
- [ ] No `WRAP_CONTENT` height on the dock pill — fixed at `100 dp`
- [ ] All `dp()` calls use the existing `private fun dp(v: Int)` helper — no raw pixels

### Accessibility (v2 — new checks)

- [ ] `TEXT_MUTED` hex is `#B3AAB8CC` — alpha `0xB3` (70%), **not** `#80AAB8CC`
- [ ] `ACCENT_TEXT = Color.parseColor("#818CF8")` declared in `companion object`
- [ ] Drawer section headers use `ACCENT_TEXT`, **not** `ACCENT`
- [ ] `animationsEnabled()` private method exists and is called before every animation
      block in §§5 7 8 9 12
- [ ] Every animation branch has an `else` that resets or snaps state immediately when
      animations are disabled
- [ ] `contrastRatio()` private method exists and is called inside `applyWallpaperTint()`
- [ ] `buildHomeLayout()` column order: clock → focus card → productivity strip →
      allowance strip → home grid → search bar → dock (**search bar is NOT first**)
- [ ] Search bar `View` itself has `contentDescription = "Search apps — opens app drawer"`
- [ ] Search icon inner `View` has `contentDescription = "Search"`
- [ ] Mic icon inner `View` has `contentDescription = "Voice search"`
- [ ] All Apps circle `View` has `contentDescription = "All apps"`
- [ ] FocusFlow "F" circle `View` has `contentDescription = "Open FocusFlow"`
- [ ] Blocked home grid icons: `item.contentDescription = "${label}, blocked"`
- [ ] Blocked dock icons: `iconView.contentDescription = "${label}, blocked"`
- [ ] Blocked drawer icons: `appHolder.itemView.contentDescription = "${item.label}, blocked"`
- [ ] Productivity strip chip height is **44 dp** — no less
- [ ] Clock: `clockView` (hours) color = `TEXT_DIM`; `minuteView` (minutes) = `TEXT_PRIMARY`
- [ ] `wallpaperAccent` is only applied when `contrastRatio(candidate, darkBg) >= 3.0`;
      falls back to `ACCENT` otherwise

---

## Priority order [REVISED]

Implement in this order if time is limited — each section yields more than the one after:

1. **§0 + §1** — Accessibility helpers + design tokens. Implement in one pass; §0
   helpers are called by six other sections so they must exist first.
2. §8 — Dock rebuild (most visible on every unlock)
3. §4 — Clock redesign (second most visible)
4. §3 — Search bar (repositioned + accessibility)
5. §7 — Home grid (blocked contentDescriptions + animation fix)
6. §5 — Focus session card (brand identity + animation guard)
7. §9 — Drawer (ACCENT_TEXT headers + animation guards + contentDescriptions)
8. §6 — Productivity strip (FocusFlow-specific; chip height fix)
9. §10 — Ripple feedback (finish)
10. §11 — Wallpaper tinting (delight; contrast guard)
11. §12 — Fling physics (premium feel; animation guard)
12. §2 — Three-layer gradient (subtle depth, do last)
