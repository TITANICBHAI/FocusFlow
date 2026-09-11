# FocusFlow Bug Diagnosis — Verified from redo.zip

Every claim below is traced to a specific file and line in `redo.zip`.
Nothing is carried from the previous diagnosis doc without re-verification.
Where I couldn't confirm something, I say so explicitly.

---

## TYPESCRIPT / REACT NATIVE LAYER

---

### BUG 1 — Session PIN: hash verified but silently discarded at call sites

**What the native side does (confirmed, SharedPrefsModule.kt:52–66 and ForegroundServiceModule.kt:100–114):**
Both `publishFocusSnapshot` and `stopService` check for a stored session PIN hash.
If one exists and the supplied `pinHash` doesn't match, they reject with `PIN_REQUIRED`.
If no session PIN is stored, they pass through unconditionally.

**`PinVerifyModal` is correct (PinVerifyModal.tsx:89–102):**
On success it calls `onVerified(hash)` — the SHA-256 hex of the entered password IS
passed to the callback. This component is not the bug.

**Call sites that receive the hash but discard it:**
- `focus.tsx:607` — `onVerified={() => { setFocusStopPinVisible(false); stopFocusMode(); }}`
  The hash parameter is never declared. `stopFocusMode()` gets `null`.
- `active.tsx:455` — `onVerified={() => { setFocusPinVisible(false); void stopFocusMode(); }}`
  Same problem.

**Call sites that don't prompt for a PIN at all:**
- `focus.tsx:328` — Alert.alert "Stop" button: `void stopFocusMode()`
- `focus.tsx:515` — Alert.alert "Stop" button: `stopFocusMode()`
- `focus.tsx:549` — emergency override: `await stopFocusMode()` (after logging the
  override to the DB, no PIN check before this)

**Fix for the above five:** the two `PinVerifyModal` call sites need `onVerified={(hash) => { ... stopFocusMode(hash); }}`. The three Alert.alert / emergency-override call sites need a PIN prompt added before they proceed (reusing the existing `focusStopPinVisible` flow), rather than calling `stopFocusMode()` directly.

**Authorized paths — these must NOT require a PIN, and need a native bypass:**
- `AppContext.tsx:1483` — `completeTask` calls `await stopFocusMode()` when completing
  normally (or when `keepFocusActiveUntilTaskEnd` is false).
- `AppContext.tsx:1533` — `skipTask` calls `await stopFocusMode()`.
- `AppContext.tsx:956` — the 30-second tick calls `void stopFocusMode()` when
  `shouldForceClearSession` returns true (orphaned session cleanup).

These three represent legitimate, system-authorized session endings, not user
circumvention. The session PIN's purpose is to block casual "just turn it off"
circumvention — it should never block these paths.

**Required Kotlin change (this bug can't be fully fixed from TS alone):**
Add an authorized bypass to both `stopService` and `publishFocusSnapshot` in Kotlin.
The cleanest option: a separate `stopServiceInternal(promise)` and matching
`publishFocusSnapshotInternal(...)` that skip the PIN check entirely, only callable
from these three paths via a new TS wrapper. Do NOT remove the PIN check from the
existing methods — those are still needed for user-initiated stops.

---

### BUG 2 — keepFocusActiveUntilTaskEnd toggle has no PIN gate

**File:** `app/(tabs)/defense.tsx:437–452`

The toggle is a plain `Switch` inside a `SettingRow`, calling `update()` directly:
```tsx
onValueChange={(value) => void update({ keepFocusActiveUntilTaskEnd: value })}
```

Every neighboring toggle uses `ProtectedToggle` / `toggleProtectedSetting`. The
`toggleProtectedSetting` helper (line 137–163) only covers a specific key union that
does not include `keepFocusActiveUntilTaskEnd` — so it can't be plugged in without
also extending that union.

**Gate type:** session PIN (not defense PIN). If a session PIN is set, turning this
toggle OFF requires the session PIN. If no session PIN is configured, no gate.

**Required addition:** a `requireSessionPin` helper in `defense.tsx` parallel to the
existing `requireDefensePin` (line 104–135), but checking `SessionPinModule.verifyPin`
instead of `SharedPrefsModule.getString('defense_pin_hash')`. Turning ON is always
permitted without a gate. Turning OFF goes through `requireSessionPin` if one is
configured, otherwise proceeds directly.

---

### BUG 3 — deleteTask has no PIN gate and no session cleanup

**File:** `src/context/AppContext.tsx:1373–1402`

`deleteTask` does three things: compresses the schedule, deletes from DB, reschedules
alarms. It never checks whether the deleted task has an active focus session, and
never calls `stopFocusMode()`. It also has no PIN gate of any kind.

**Two fixes:**
1. If `state.focusSession?.taskId === taskId`, call `stopFocusMode()` using the
   authorized internal path from Bug 1's fix (not a user PIN prompt — this is a
   system-authorized path same as completeTask).
2. Gate the delete action with the session PIN (same `requireSessionPin` helper from
   Bug 2) when a session PIN is configured. If none is configured, no gate.

**Also:** `compressDeletedTaskGap` at line 1379 runs unconditionally. Once the Auto
Reschedule toggle exists (Bug 8), this call gets wrapped in
`if (settings.autoRescheduleEnabled)`.

---

### BUG 4 — No overlap warning when starting a task while another is active

**File:** `src/context/AppContext.tsx:1639–1681`

`startFocusMode` has no check for an existing active session before calling
`_startFocusMode`. Inside `focusService.ts:39`, `if (focusActive) await stopFocusMode()`
silently ends the first session — no hash, no prompt.

**Fix:** before calling `_startFocusMode`, check `state.focusSession?.isActive`. If
true and for a different task, show an `Alert.alert` confirmation naming the currently
active task. Only proceed — and only then call the existing stop-then-start flow —
if the user confirms. The stop in that case should use the authorized internal path
(Bug 1), not prompt for a PIN again, since the user just explicitly chose to switch.

---

### BUG 5 — No custom duration input in EditTaskModal

**File:** `src/components/EditTaskModal.tsx:40–46, 253–278`

Five fixed presets (25m, 45m, 1h, 1h30m, 2h), rendered as chip buttons. No custom
entry path. `durationStr` is already a plain string state (`useState(String(task.durationMinutes))`)
that accepts any numeric value — the task's current duration is preserved correctly
on save regardless of whether it matches a preset. The missing piece is purely UI:
no way to type an arbitrary number of minutes.

**Fix:** add a "Custom" chip after the five presets. When selected, show a `TextInput`
(numeric keyboard) that updates `durationStr` directly. Keep the existing preset
chips working as-is — tapping a preset still sets `durationStr` to that value, which
also deselects "Custom" naturally since the TextInput value would differ.

---

### BUG 6 — Auto Reschedule toggle missing (feature, not regression)

**Files affected:** `src/data/types.ts`, `src/data/defaultSettings.ts`,
`app/(tabs)/defense.tsx`, `src/context/AppContext.tsx`

`compressSchedule` is called unconditionally from `completeTask` (line 1432) and
`skipTask` (line 1507). `compressDeletedTaskGap` is called unconditionally from
`deleteTask` (line 1379). The compression logic itself is correct — verified in
`schedulerEngine.ts:259–319`. No toggle exists yet.

**What to add:**
1. New field in `AppSettings`: `autoRescheduleEnabled: boolean` (default `false`).
2. New `SettingRow` + `Switch` in `defense.tsx` under the "Focus Session Behaviour"
   section, directly below the `keepFocusActiveUntilTaskEnd` row. No PIN gate per
   your instruction.
3. Wrap all three call sites:
   `if (state.settings.autoRescheduleEnabled) { const compressed = compressSchedule(...) }`
   When off, the calls are simply skipped — no schedule changes on complete/skip/delete.

---

### BUG 7 — Keep-Focus + Auto-Reschedule interaction edge case

When `keepFocusActiveUntilTaskEnd` is on and a task completes early, `compressionTime`
is set to `updated.endTime` (the original end time), not the real completion time
(`AppContext.tsx:1430-1432`). This means `savedMinutes` in `compressSchedule` is 0
and no tasks move. This specific combination is already safe.

**The real gap (confirmed in schedulerEngine.ts:271–279):** `compressSchedule`'s
filter checks `dayjs(t.startTime).isBefore(plannedEnd)` to exclude tasks from
shifting. It does NOT check whether any OTHER task currently has a live session.
If Task Z finishes early, its savings could shift Task B's start to before Task A's
kept-alive session ends — the filter only compares against Task Z's timing, not
any other task's live session state.

**Arbitration rule (confirmed, final):** fewer allowed apps wins. Tie means keep
the current session running — no switch, since switching would accomplish nothing.
Deferred task keeps its full original duration — both start and end shift by the
delay. These three points are final; no open questions remain on this item.

**Implementation spec — confirmed against types.ts and AppContext.tsx:1646:**

The rule is "fewer allowed apps," but `focusAllowedPackages` has an empty-array
ambiguity that makes a naive `.length` comparison wrong:
```
task.focusAllowedPackages === undefined  →  resolved list = settings.allowedInFocus
task.focusAllowedPackages === []         →  means "all apps allowed" — treat as
                                            length = Infinity (LOSES to everything)
task.focusAllowedPackages = [...]        →  resolved list = that array
```
So: resolve each task's effective allowed-app list using the rule above, then compare
lengths. Shorter resolved list = fewer allowed = stricter = wins. Tie = keep current.

When a rescheduled task's new start time falls inside another task's live session:
- Incoming task is stricter (fewer allowed apps) → current session ends early via
  the authorized internal stop from Bug 1, incoming task starts immediately.
- Current session is stricter or tied → incoming task start is held until the current
  session's actual end, then starts normally with full duration preserved.
- Either outcome: passive notification, not a blocking dialog.

The 30-second tick at `AppContext.tsx:954` is the right place to check for a deferred
task whose blocking condition has cleared.

---

## KOTLIN / NATIVE LAYER

---

### BUG 8 — App icons are square, not circular

**File:** `LauncherActivity.kt:1776–1778`

```kotlin
private fun getAppIcon(pkg: String): Drawable? =
    try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
```

Set directly via `holder.icon.setImageDrawable(...)` at:
- Line 300 (Classic drawer bind)
- Line 321 (Glassy drawer bind)
- Line 740 (allowance-row icon in `refreshTodaysLimits`)

No circular clip anywhere.

**Fix:** one shared helper function:
```kotlin
private fun getRoundIcon(pkg: String): Drawable? {
    val raw = try { packageManager.getApplicationIcon(pkg) } catch (_: Exception) { return null }
    val bmp = Bitmap.createBitmap(raw.intrinsicWidth.coerceAtLeast(1),
                                  raw.intrinsicHeight.coerceAtLeast(1),
                                  Bitmap.Config.ARGB_8888)
    raw.setBounds(0, 0, bmp.width, bmp.height)
    raw.draw(Canvas(bmp))
    return RoundedBitmapDrawableFactory.create(resources, bmp).also { it.isCircular = true }
}
```
Replace all three `getAppIcon(...)` call sites with `getRoundIcon(...)`. No other
changes needed.

---

### BUG 9 — Classic drawer: app names invisible or very narrow

**File:** `LauncherActivity.kt:235–244`

The divider `View` is the third child in a horizontal `LinearLayout`, with:
```kotlin
layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,  // width
    1,                                        // height
)
```
`weight = 0` (default), `width = MATCH_PARENT`. In a horizontal LinearLayout, Android
measures non-weighted children first. A `MATCH_PARENT` child in the primary direction
with no weight is measured against the remaining space after fixed children — but
because the label has `weight = 1f` and `width = 0`, the layout engine must resolve
weight allocation separately. The result: the divider effectively consumes all
available width, leaving the label at or near zero. Confirmed behavior matches the
reported symptom (names invisible).

**Fix:** remove the `row.addView(...)` divider entirely. Replace it with a bottom
stroke on the row background:
```kotlin
val row = LinearLayout(parent.context).apply {
    ...
    background = LayerDrawable(arrayOf(
        ColorDrawable(Color.TRANSPARENT),   // fill
        object : Drawable() {               // bottom border only
            override fun draw(canvas: Canvas) {
                val p = Paint().apply { color = CLASSIC_BORDER; strokeWidth = 1f }
                canvas.drawLine(0f, bounds.bottom.toFloat(),
                                bounds.right.toFloat(), bounds.bottom.toFloat(), p)
            }
            override fun setAlpha(a: Int) = Unit
            override fun setColorFilter(cf: ColorFilter?) = Unit
            @Deprecated("") override fun getOpacity() = PixelFormat.TRANSPARENT
        }
    ))
}
```
Or simpler: use a `DividerItemDecoration` on the `RecyclerView` itself for Classic
theme, which keeps row construction clean.

---

### BUG 10 — Glassy drawer: labels crowd into next row, cell height too rigid

**File:** `LauncherActivity.kt:248–293` (createGlassyHolder) and `1017–1036` (openDrawer)

Two separate problems:

**A — No row spacing:** `recycler.layoutManager = GridLayoutManager(this, columns)` at
line 1032 with no `addItemDecoration`. Cells are edge-to-edge except for `dp(4)` top
and bottom padding inside the cell itself, giving ~8dp total between rows — not enough
at larger icon scales.

**Fix A:** after line 1033, add:
```kotlin
recycler.addItemDecoration(object : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View,
                                parent: RecyclerView, state: RecyclerView.State) {
        outRect.bottom = dp(10)
    }
})
```

**B — Fixed cell height ignores icon scale:** cell is `dp(92)` fixed at line 252.
Icon size changes at bind time (`(42f * iconScale).toInt()`, line 316) but the cell
box never grows. At `iconScale = 1.2`, icon is 50dp in a 92dp cell — the label at
the bottom has ~42dp including icon + padding. Tight, but not visually overlapping at
default scale. At `iconScale = 1.4+` it does overlap.

**Fix B:** make cell height dynamic. In `onBindViewHolder` for Glassy (line 315), after
setting the icon's layout params, also update the cell's height:
```kotlin
val cellHeight = dp((iconSize + 32).coerceAtLeast(88))  // icon + label space + margin
holder.itemView.layoutParams = (holder.itemView.layoutParams).also {
    it.height = cellHeight
}
```

---

### BUG 11 — Swipe-up opens notification shade while drawer is open; no swipe-to-close

**File:** `LauncherActivity.kt:1737–1744`

```kotlin
if (dy > dp(60) && velocity < -250f && !isDrawerOpen) {   // ← has guard
    openDrawer()
    return true
}
if (dy < -dp(80) && velocity > 250f) {                    // ← NO guard
    expandNotificationsPanel()
    return true
}
```

The downward-swipe branch fires unconditionally. A normal scroll-down gesture in the
app list — a daily action — has exactly the direction and velocity this watches for.

**Fix:** restructure as mutually exclusive branches:
```kotlin
if (dy > dp(60) && velocity < -250f && !isDrawerOpen) {
    openDrawer(); return true
}
if (dy < -dp(80) && velocity > 250f) {
    if (isDrawerOpen) { closeDrawer(); return true }
    expandNotificationsPanel(); return true
}
```
When drawer is open: downward swipe closes it. When on home screen: downward swipe
opens the notification shade (existing behavior, preserved).

---

### BUG 12 — Purple strip in status bar

**File:** `LauncherActivity.kt:390`

Only `FLAG_SHOW_WALLPAPER` is added. `window.statusBarColor` is never set.
`WindowCompat.setDecorFitsSystemWindows` is never called. The status bar renders the
app theme's default primary color.

**Fix — add to `onCreate()` immediately after `window.addFlags(FLAG_SHOW_WALLPAPER)`:**
```kotlin
window.statusBarColor = android.graphics.Color.TRANSPARENT
WindowCompat.setDecorFitsSystemWindows(window, false)
```
Then handle the top inset so content doesn't clip under the transparent status bar:
apply `WindowInsetsCompat` padding to `rootFrame` or the top-level scroll view.

**Caveat:** I can't see `styles.xml` in this zip. If the theme declares
`android:windowTranslucentStatus` or other status-bar flags, they may conflict —
check for that before applying this fix.

---

### BUG 13 — Daily allowance list has no scroll cap

**File:** `LauncherActivity.kt:703–773`

`refreshTodaysLimits()` builds one `LinearLayout` card and appends every allowance
row into it with `card.addView(row)` in a loop. The card's height is `WRAP_CONTENT`.
No `ScrollView`/`NestedScrollView`, no max-height constraint. 4+ entries push the
rest of the home screen down.

**Fix:** replace the inner loop's target with a `NestedScrollView` wrapping a
`LinearLayout`, capped at 3 rows' height:
```kotlin
val innerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
val scrollWrap = NestedScrollView(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(52 * 3 + 16),   // ~3 rows visible, adjust to actual row height
    )
    isNestedScrollingEnabled = true
}
// add rows to innerList, add innerList to scrollWrap, add scrollWrap to card
```

---

### BUG 14 — Glassy theme: home screen visible through everything

**File:** `LauncherActivity.kt:944–960, 1258–1271, 1811–1815`

The Glassy drawer scrim: `#66000000` = 40% black over the wallpaper (line 945).
The drawer sheet: `#220E1422` = 13% opacity (line 960).
`layeredGlassBackground()` at line 1812: `GLASS_MID` = `#2AFFFFFF` = 16% white + border.
No `RenderEffect` blur anywhere in the file — confirmed by exhaustive search.

This also affects `buildCustomizeDrawerSheet()` at line 1261 (same `layeredGlassBackground`).

**Fix — two parts:**

**A — Real blur on API 31+:**
```kotlin
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
    wallpaper.setRenderEffect(
        android.graphics.RenderEffect.createBlurEffect(22f, 22f,
            android.graphics.Shader.TileMode.CLAMP)
    )
}
```
Applied to the wallpaper `ImageView` that's already being loaded in `openDrawer()`
(line 942) and in `buildCustomizeDrawerSheet` if it has its own wallpaper layer.
On pre-31 devices, skip blur — the higher opacity below compensates.

**B — Raise sheet opacity significantly:**
Change the drawer sheet background from `#220E1422` (13%) to `#CC0E1422` (80%).
Change `GLASS_MID` used in `layeredGlassBackground` from `#2AFFFFFF` (16%) to
`#99FFFFFF` (60%) for the sheet panels.

These values will likely need visual tuning — the point is the direction, not the
exact hex. Start here and adjust if it looks too opaque.

---

## DELIBERATELY OUT OF SCOPE

| Item | Reason |
|---|---|
| Alarm lateness on idle phones | OEM battery-management behavior. `TaskAlarmModule` already uses the maximum-effort alarm ladder. Not an in-app bug. |

