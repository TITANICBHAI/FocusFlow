# FocusFlow — Features & Fixes Plan

> Agent execution plan. Read the full document before touching any file.
> All JS/TS is in the repo root. Native Kotlin lives in
> `android-native/` and is synced to `android/` by `install.sh` —
> **never edit `android/` directly.**

---

## Architecture quick-reference

| Concern | Location |
|---|---|
| Theme system | `src/hooks/useTheme.ts` → returns `theme.{background,card,surface,border,text,textSecondary,muted,isDark}` |
| Dark colors | `COLORS.darkBackground`, `COLORS.darkCard`, `COLORS.darkSurface`, `COLORS.darkBorder`, `COLORS.darkText` in `src/styles/theme.ts` |
| Dark-aware pattern | Static `StyleSheet` for layout/spacing only; all color props as inline overrides from `theme` |
| Native modules | `android-native/…/modules/*.kt` + matching `src/native-modules/*.ts` wrapper |
| Manifest changes | Document in `android-native/manifest_additions.xml`; apply patch in `install.sh` |
| Backup format | `BACKUP_FILE_EXT = '.focusflow'`; `parseBackupJson(text)` → `restoreFromJson(text, cb)` in `src/services/backupService.ts` |
| SharedPrefs keys | Use `net_block_*` prefix for VPN coordinator keys |
| VPN coordinator | `VpnPolicyCoordinator.kt` — do not refactor; only add new source |
| Tick cadence | 30-second interval in AppContext; schedule-related syncs go here |

---

## §1  Dark mode fixes

Three separate root causes; fix independently, no cross-dependencies.

### 1.1  TaskCard (`src/components/TaskCard.tsx`)

**Problem:** `StyleSheet.create` uses hardcoded `COLORS.card`, `COLORS.surface`,
`COLORS.border`, `COLORS.text`. No `useTheme()` call. Every task card renders
light regardless of the dark toggle.

**Fix:**

Add `const { theme } = useTheme();` at the top of the component function.

For every `<View>` or `<Text>` that currently references a colour constant in its
`style` prop, split into: (a) the static `styles.*` entry for layout
(padding/margin/radius/flex), and (b) an inline override for the colour. Examples:

```tsx
// card container
<View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>

// app label / title text
<Text style={[styles.title, { color: theme.text }]}>

// subtitle / meta text
<Text style={[styles.meta, { color: theme.muted }]}>

// progress bar track
<View style={[styles.track, { backgroundColor: theme.border }]}>
```

Remove `backgroundColor` and `color` from the `StyleSheet.create` entries that
receive inline overrides — keep only layout properties there. Brand/semantic
colours (`COLORS.primary`, task color, priority badge alpha) stay as-is.

### 1.2  AppPickerSheet (`src/components/AppPickerSheet.tsx`)

**Problem:** Same as above — `StyleSheet.create` with hardcoded `COLORS.*` light
constants, no `useTheme()`. Affects the Focus-mode allowed-apps picker and
anywhere else the sheet appears (`AllowedAppsModal` wraps it directly).

**Fix:**

Add `const { theme } = useTheme();` at the top of the function component.

Apply the same split pattern as §1.1 to every colour-carrying element:

| Element | Old constant | New inline key |
|---|---|---|
| Modal outer / sheet container | `COLORS.background` | `theme.background` |
| App row / card | `COLORS.card` | `theme.card` |
| Search input background | `COLORS.surface` | `theme.surface` |
| Search input border | `COLORS.border` | `theme.border` |
| Search input text | `COLORS.text` | `theme.text` |
| Placeholder / caption text | `COLORS.muted` | `theme.muted` |
| Section header text | `COLORS.textSecondary` | `theme.textSecondary` |
| Divider / separator | `COLORS.border` | `theme.border` |

The `Modal` component's top-level wrapping `View` must also receive
`backgroundColor: theme.background` — this covers the drag-handle and safe-area
gap that shows behind the sheet.

Selected-chip tints (`COLORS.primary + '14'`, `COLORS.primary + '22'`) are brand
colours and stay unchanged.

### 1.3  Focus tab active-task background (`app/(tabs)/focus.tsx`)

**Problem:** The `SafeAreaView` uses `task.color + '18'` (10 % opacity tint) as
background for the whole screen when a session is running. On a dark surface this
creates an intrusive colour bleed that looks like a rendering artefact.

**Fix:**

Reduce opacity in dark mode and move the tint to the card border only:

```tsx
// SafeAreaView background — 5 % in dark, 10 % in light
<SafeAreaView
  style={[
    styles.safe,
    {
      backgroundColor: task
        ? task.color + (theme.isDark ? '0D' : '18')
        : theme.background,
    },
  ]}
>

// Task panel card — tinted border, not background fill
<View
  style={[
    styles.taskPanel,
    {
      backgroundColor: theme.card,
      borderColor: task ? task.color + (theme.isDark ? '44' : '66') : theme.border,
    },
  ]}
>
```

---

## §2  `.focusflow` file association

The backup format, `parseBackupJson`, and `restoreFromJson` are fully implemented.
The only missing pieces are: a native method to read a URI handed by the OS, a
manifest intent filter, and a JS handler for the incoming link.

### 2.1  Native — add `readUri` to `NativeFilePickerModule.kt`

`android-native/…/modules/NativeFilePickerModule.kt`

The existing `handlePickResult` already uses `contentResolver.openInputStream(uri)`.
Add a new `@ReactMethod` that does the same for a caller-supplied URI string — this
is how `ACTION_VIEW` intents deliver files:

```kotlin
@ReactMethod
fun readUri(uriString: String, promise: Promise) {
    try {
        val uri = android.net.Uri.parse(uriString)
        val content = reactApplicationContext.contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
        if (content == null) {
            promise.reject("E_READ_FAILED", "Could not open URI stream")
            return
        }
        promise.resolve(content)
    } catch (e: Exception) {
        promise.reject("E_READ_FAILED", e.message ?: "Unknown error")
    }
}
```

### 2.2  TS binding — `src/native-modules/NativeFilePickerModule.ts`

Add `readUri` to the `NativeFilePicker` type and the module wrapper:

```typescript
// In the NativeModules cast:
NativeFilePicker: {
  pickFile(mimeType: string): Promise<{ name: string; content: string } | null>;
  saveFile(content: string, fileName: string, mimeType: string): Promise<string | null>;
  readUri(uri: string): Promise<string | null>;   // ← add
};

// In NativeFilePickerModule object:
async readUri(uri: string): Promise<string | null> {
  if (Platform.OS !== 'android') return null;
  if (!NativeFilePicker?.readUri) return null;
  return NativeFilePicker.readUri(uri);
},
```

### 2.3  Manifest intent filter

`android-native/manifest_additions.xml` — add this documentation block inside
the `<activity android:name=".MainActivity">` section:

```xml
<!-- .focusflow file association — opens when user taps a backup file -->
<intent-filter android:label="Open FocusFlow backup">
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="application/octet-stream" />
</intent-filter>
<intent-filter android:label="Open FocusFlow backup">
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="*/*" />
</intent-filter>
```

In `install.sh`, add a patch step that inserts these filters into
`android/app/src/main/AndroidManifest.xml` inside the `<activity … MainActivity>`
block, before the closing `</activity>`. Use `sed` or `xmlstarlet` consistent with
the existing patch steps already in `install.sh`.

**Note:** Both filters are needed because different Android file managers send
different MIME types for unknown extensions. The JS handler validates the content
is a genuine `.focusflow` file before proceeding, so matching broadly is safe.

### 2.4  JS handler — `app/_layout.tsx`

Add a `handleIncomingUri` helper and wire it to both cold-start and foreground
resume scenarios. Place this logic in the root layout so it is always mounted.

```typescript
import { useEffect, useCallback } from 'react';
import { Linking, Alert } from 'react-native';
import { parseBackupJson, restoreFromJson } from '@/services/backupService';
import { NativeFilePickerModule } from '@/native-modules/NativeFilePickerModule';

// Inside the root layout component:
const handleIncomingUri = useCallback(async (url: string | null) => {
  if (!url) return;
  // Only handle content:// or file:// URIs — skip deep links and other schemes
  if (!url.startsWith('content://') && !url.startsWith('file://')) return;

  let content: string | null;
  try {
    content = await NativeFilePickerModule.readUri(url);
  } catch {
    Alert.alert('Could not open file', 'FocusFlow was unable to read this file.');
    return;
  }
  if (!content) return;

  const parsed = parseBackupJson(content);
  if (!parsed.ok) {
    Alert.alert('Invalid file', parsed.error);
    return;
  }

  // Navigate to the existing import confirmation flow, passing the content.
  // Use the same route/modal that pickAndImportBackup triggers on success —
  // replace this with the actual navigation call used in your settings screen.
  router.push({ pathname: '/import-confirm', params: { content } });
}, [router]);

useEffect(() => {
  // Cold start: app was opened by tapping the file
  void Linking.getInitialURL().then(handleIncomingUri);

  // Warm start: app was already running, brought to foreground by the file
  const sub = Linking.addEventListener('url', ({ url }) => void handleIncomingUri(url));
  return () => sub.remove();
}, [handleIncomingUri]);
```

### 2.5  Import confirmation

Create `app/import-confirm.tsx` as a modal route. It receives `content` via params,
calls `restoreFromJson(content, callbacks)` on confirm, and navigates back on
cancel. Use the same `RestoreCallbacks` shape that `pickAndImportBackup` already
constructs in the settings screen — extract those callbacks into a shared
`buildRestoreCallbacks()` helper in `src/services/backupService.ts` to avoid
duplication.

Show a summary before committing:
- Number of tasks in the backup
- Settings that will be overwritten
- "Replace all tasks" / "Merge (keep existing)" toggle
- "Import" (destructive, red) and "Cancel" buttons

---

## §3  Group schedule VPN

**Constraint:** Do not touch the VPN coordinator architecture. Add one new data
source only. Do not add per-app VPN granularity to the schedule — one toggle per
schedule is enough and keeps the modal clean.

### 3.1  Add `vpnEnabled` to `GreyoutWindow` type (`src/data/types.ts`)

```typescript
export interface GreyoutWindow {
  pkg: string;
  pkgs?: string[];
  startHour: number;
  startMin: number;
  endHour: number;
  endMin: number;
  days: number[];
  scheduleId?: string;
  scheduleName?: string;
  vpnEnabled?: boolean;    // ← add: if true, all pkgs in this window get VPN-blocked
}
```

Also add `vpnEnabled?: boolean` to `RecurringBlockSchedule` in the same file if
it is not already present (it already exists in the current type — verify and leave
it if so).

### 3.2  VPN toggle UI in `GreyoutScheduleModal` (`src/components/GreyoutScheduleModal.tsx`)

Add one piece of state:

```typescript
const [vpnEnabled, setVpnEnabled] = useState<boolean>(draft?.vpnEnabled ?? false);
```

In the "add/edit window" form, after the app-selection section and before the
Save button, add a single row:

```tsx
<View style={[styles.settingRow, { borderTopColor: theme.border }]}>
  <View style={styles.settingLeft}>
    <Text style={[styles.settingLabel, { color: theme.text }]}>
      Block network (VPN)
    </Text>
    <Text style={[styles.settingSub, { color: theme.muted }]}>
      Cut internet access for these apps during this window
    </Text>
  </View>
  <Switch
    value={vpnEnabled}
    onValueChange={setVpnEnabled}
    trackColor={{ true: COLORS.primary, false: theme.border }}
    thumbColor={COLORS.card}
  />
</View>
```

When building the saved `GreyoutWindow` on confirm, include `vpnEnabled`:

```typescript
const window: GreyoutWindow = {
  ...draft,
  pkg: pkgs[0] ?? '',
  pkgs,
  vpnEnabled,    // ← add
};
```

When populating the form for editing an existing window, seed the state:

```typescript
setVpnEnabled(existingWindow.vpnEnabled ?? false);
```

### 3.3  Preserve `vpnEnabled` through the schedule conversion

Wherever `RecurringBlockSchedule` objects are converted to `GreyoutWindow[]` (in
AppContext or defense.tsx), propagate `vpnEnabled` from schedule → window:

```typescript
const window: GreyoutWindow = {
  pkg: ...,
  pkgs: schedule.packages,
  ...timing,
  scheduleId: schedule.id,
  scheduleName: schedule.name,
  vpnEnabled: schedule.vpnEnabled ?? false,   // ← add
};
```

And the reverse (window → schedule on save):

```typescript
const schedule: RecurringBlockSchedule = {
  ...existingSchedule,
  packages: window.pkgs ?? [window.pkg],
  vpnEnabled: window.vpnEnabled ?? false,     // ← add
  vpnPackages: [],   // empty = block all packages listed; already in the type
};
```

### 3.4  `publishScheduleVpnSnapshot` — `SharedPrefsModule.kt`

Add one new `@ReactMethod` to
`android-native/…/modules/SharedPrefsModule.kt`:

```kotlin
@ReactMethod
fun publishScheduleVpnSnapshot(packagesJson: String, promise: Promise) {
    try {
        val ok = prefs().edit()
            .putString("net_block_schedule_vpn_pkgs", packagesJson)
            .commit()
        if (!ok) {
            promise.reject("WRITE_FAILED", "commit() returned false")
            return
        }
        VpnPolicyCoordinator.requestSync(reactApplicationContext)
        promise.resolve(null)
    } catch (e: Exception) {
        promise.reject("PREFS_ERROR", e.message, e)
    }
}
```

Add the matching entry to `src/native-modules/SharedPrefsModule.ts`:

```typescript
async publishScheduleVpnSnapshot(packagesJson: string): Promise<void> {
  if (!hasSharedPrefsMethod('publishScheduleVpnSnapshot')) return;
  await callNativeStrict('publishScheduleVpnSnapshot', () =>
    SharedPrefs.publishScheduleVpnSnapshot(packagesJson),
  );
},
```

### 3.5  AppContext — sync active schedule VPN packages

Add a `lastScheduleVpnRef = useRef<string>('[]')` to prevent unnecessary
SharedPrefs writes on every tick.

Add a `syncScheduleVpn()` callback:

```typescript
const syncScheduleVpn = useCallback(async () => {
  const now        = new Date();
  const nowDay     = now.getDay() + 1;   // Calendar.DAY_OF_WEEK: 1=Sun…7=Sat
  const nowMinutes = now.getHours() * 60 + now.getMinutes();

  const active = new Set<string>();
  for (const w of stateRef.current.greyoutWindows ?? []) {
    if (!w.vpnEnabled) continue;
    if (!w.days.includes(nowDay)) continue;
    const start = w.startHour * 60 + w.startMin;
    const end   = w.endHour   * 60 + w.endMin;
    if (nowMinutes >= start && nowMinutes < end) {
      for (const pkg of w.pkgs ?? (w.pkg ? [w.pkg] : [])) active.add(pkg);
    }
  }

  const json = JSON.stringify([...active].sort());
  if (json === lastScheduleVpnRef.current) return;   // nothing changed
  lastScheduleVpnRef.current = json;
  await SharedPrefsModule.publishScheduleVpnSnapshot(json).catch(() => {});
}, []);
```

Call `syncScheduleVpn()` from:
- `init()` after schedule windows are loaded
- The existing `AppState 'active'` handler (foreground resume)
- The 30-second tick (it no-ops cheaply when nothing changed)

### 3.6  VPN coordinator — new source (`VpnPolicyCoordinator.kt`)

In `effectivePolicy()`, after the existing `standaloneCandidates` line, add:

```kotlin
val scheduleCandidates = parsePackageJson(
    prefs.getString("net_block_schedule_vpn_pkgs", "[]") ?: "[]"
)
```

Then add `scheduleCandidates` to the existing union that builds `sourcePackages`.
Do not change anything else in the coordinator.

---

## §4  Standalone block VPN

**Context:** `StandaloneBlockModal` is in `app/(tabs)/focus.tsx`, not `defense.tsx`.
The modal UI, `StandaloneBlockModal`'s internal state, and `focus.tsx`'s `onSave`
wiring are all correct — `vpnPackages` flows from the modal up to
`setStandaloneBlockAndAllowance` without being dropped.

**Root bug:** `publishStandaloneSnapshot` in Kotlin has no `vpnPackages` parameter
and never writes `PREF_STANDALONE_VPN_PKGS` (`"net_block_standalone_vpn_packages"`).
The coordinator reads this key correctly and auto-clears standalone candidates when
the block expires (via `isStandaloneBlockActive` check) — the architecture is sound.
The sole problem is that the key is never written, so it is always `[]`.

**VPN isolation is guaranteed:** each source writes to its own key only. The
coordinator unions them at compute time via `.distinct()`. Removing a package from
standalone never affects the explicit list or schedule VPN, and vice versa. If the
same package exists in multiple sources it remains blocked until all sources drop it.

Fix requires changes in exactly three files.

### 4.1  `SharedPrefsModule.kt`

`android-native/…/modules/SharedPrefsModule.kt`

Add `vpnPackages: ReadableArray?` as the fifth parameter of
`publishStandaloneSnapshot` (before `promise`). Write it to SharedPrefs in both
the active and inactive branches:

```kotlin
@ReactMethod
fun publishStandaloneSnapshot(
    active: Boolean,
    packages: ReadableArray,
    untilMs: Double,
    pinHash: String?,
    vpnPackages: ReadableArray?,   // ← add
    promise: Promise,
) {
    try {
        if (!active) {
            val currentUntil = prefs().getLong("standalone_block_until_ms", 0L)
            if (currentUntil > System.currentTimeMillis() &&
                rejectIfInvalidSessionPin(pinHash, "...", promise)
            ) return
        }

        val editor = prefs().edit()
        if (active) {
            editor
                .putBoolean("standalone_block_active", true)
                .putString("standalone_blocked_packages", packages.toJsonArrayString())
                .putLong("standalone_block_until_ms", untilMs.toLong())
                .putString(                                              // ← add
                    "net_block_standalone_vpn_packages",
                    vpnPackages?.toJsonArrayString() ?: "[]",
                )
        } else {
            editor
                .putBoolean("standalone_block_active", false)
                .putString("standalone_blocked_packages", "[]")
                .putLong("standalone_block_until_ms", 0L)
                .putString("net_block_standalone_vpn_packages", "[]")   // ← add
        }

        if (!editor.commit()) {
            promise.reject("PREFS_WRITE_FAILED", "commit() returned false")
            return
        }

        NetworkBlockerVpnService.requestSync(reactContext)
        FocusFlowWidget.pushWidgetUpdate(reactContext)
        promise.resolve(null)
    } catch (e: Exception) {
        promise.reject("PREFS_ERROR", e.message, e)
    }
}
```

Keep the rest of the method body exactly as it was. Do not change any other
method in `SharedPrefsModule.kt`.

### 4.2  `SharedPrefsModule.ts`

`src/native-modules/SharedPrefsModule.ts`

Add `vpnPackages` as the fifth argument to the wrapper and the native call:

```typescript
async publishStandaloneSnapshot(
  active: boolean,
  packages: string[],
  untilMs: number,
  pinHash: string | null = null,
  vpnPackages: string[] = [],          // ← add
): Promise<void> {
  if (!hasSharedPrefsMethod('publishStandaloneSnapshot')) return;
  await callNativeStrict('publishStandaloneSnapshot', () =>
    SharedPrefs.publishStandaloneSnapshot(
      active,
      packages,
      untilMs,
      pinHash,
      vpnPackages,                     // ← add
    ),
  );
},
```

### 4.3  `AppContext.tsx`

`src/context/AppContext.tsx` — inside `setStandaloneBlockAndAllowance`

`resolvedVpnPackages` is already computed at line ~1901. Pass it through to
`publishStandaloneSnapshot` at the call site (~line 1919):

```typescript
await SharedPrefsModule.publishStandaloneSnapshot(
  active,
  packages,
  untilMs ?? 0,
  pinHash,
  resolvedVpnPackages,    // ← add (already available in scope)
);
```

No other changes needed in `AppContext.tsx`.

---

## §5  Detailed report — auto-generated analysis

**Design rationale (revised):** the original design asked the user to answer
written prompts to "complete" a review — functionally an exam. This version
removes that entirely. The report is generated automatically from data
already logged: a small rule-based insight engine looks for real patterns
(a time-of-day drop-off, a recurring skip, a comparison against the user's
own recent average) and writes them up as short, specific sentences — the
way a person looking at the data would describe it, not a stats dump.

This runs fully on-device. No LLM call, no network request — sending task
and blocking history to a cloud model would work against FocusFlow's
privacy-first identity, and isn't needed here: pattern detection over a
user's own recent data is cheap, deterministic, and testable without one.

The user's only optional input is a single freeform note per day/week, with
no minimum length and no gate. Leaving it blank is the same as skipping it —
there is nothing to submit, nothing to unlock.

**DB impact:** one small table for the optional notes, pruned weekly.
Everything else — the insight engine itself — is pure computation over task
arrays the report screen already needs to fetch for its stats summary and
task breakdown. No new full-table scans, no per-render DB hits.

---

### 5.1  Insight engine — `src/services/insightEngine.ts`

New file. Pure functions only — no DB access, no side effects. The report
screen (§5.3) fetches the task arrays and passes them in.

```typescript
import dayjs from 'dayjs';
import type { Task } from '@/data/types';

export interface Insight {
  id: string;             // detector id — stable, safe as a React key
  text: string;           // ready-to-render sentence
  significance: number;   // 0–1, ranking only — never shown to the user
}

export interface AnalysisResult {
  headline: string;
  insights: Insight[];
}

function completionRate(tasks: Task[]): number {
  if (tasks.length === 0) return 0;
  return tasks.filter((t) => t.status === 'completed').length / tasks.length;
}

// ── Detectors — each takes task arrays, returns one Insight or null ────
// null means "didn't fire" (not enough data, or no meaningful pattern).
// These are written generically enough to run on either a single day's
// tasks or a full week's tasks — computeDailyAnalysis and
// computeWeeklyAnalysis below reuse the same detector where it applies.

/** Splits tasks at a 2 PM cutoff and compares completion rate either side. */
function detectTimeOfDaySplit(tasks: Task[]): Insight | null {
  const cutoffHour = 14;
  const before = tasks.filter((t) => dayjs(t.startTime).hour() < cutoffHour);
  const after  = tasks.filter((t) => dayjs(t.startTime).hour() >= cutoffHour);
  if (before.length < 2 || after.length < 2) return null;

  const rBefore = completionRate(before);
  const rAfter  = completionRate(after);
  const delta   = Math.abs(rBefore - rAfter);
  if (delta < 0.25) return null;   // not a meaningful difference

  const strongerPeriod = rBefore > rAfter ? 'morning' : 'afternoon';
  const weakerPeriod    = rBefore > rAfter ? 'afternoon' : 'morning';
  return {
    id: 'time-of-day-split',
    text: `Completion held strong in the ${strongerPeriod} (${Math.round(Math.max(rBefore, rAfter) * 100)}%) but dropped in the ${weakerPeriod} (${Math.round(Math.min(rBefore, rAfter) * 100)}%).`,
    significance: Math.min(delta, 0.9),
  };
}

/** Flags a task title skipped repeatedly within the given task set. */
function detectRecurringSkip(tasks: Task[]): Insight | null {
  const skipCounts = new Map<string, number>();
  for (const t of tasks) {
    if (t.status !== 'skipped') continue;
    skipCounts.set(t.title, (skipCounts.get(t.title) ?? 0) + 1);
  }
  const top = [...skipCounts.entries()].sort((a, b) => b[1] - a[1])[0];
  if (!top || top[1] < 3) return null;
  const [title, count] = top;

  return {
    id: 'recurring-skip',
    text: `"${title}" has been skipped ${count} times recently — worth reconsidering its time slot.`,
    significance: 0.6 + Math.min(count * 0.05, 0.25),
  };
}

/** Compares a day's focus minutes to the trailing baseline average. */
function detectBaselineComparison(dayTasks: Task[], baselineTasks: Task[]): Insight | null {
  const todayFocus = dayTasks
    .filter((t) => t.status === 'completed' && t.focusMode)
    .reduce((s, t) => s + t.durationMinutes, 0);

  const byDay = new Map<string, number>();
  for (const t of baselineTasks) {
    if (t.status !== 'completed' || !t.focusMode) continue;
    const d = dayjs(t.startTime).format('YYYY-MM-DD');
    byDay.set(d, (byDay.get(d) ?? 0) + t.durationMinutes);
  }
  if (byDay.size < 5) return null;   // not enough history yet
  const avg = [...byDay.values()].reduce((s, m) => s + m, 0) / byDay.size;
  if (avg === 0) return null;

  const diffPct = (todayFocus - avg) / avg;
  if (Math.abs(diffPct) < 0.15) return null;

  const direction = diffPct > 0 ? 'more' : 'less';
  return {
    id: 'baseline-comparison',
    text: `${todayFocus} minutes of focus time today — ${Math.round(Math.abs(diffPct) * 100)}% ${direction} than your recent daily average.`,
    significance: Math.min(Math.abs(diffPct), 0.85),
  };
}

/** Detects a strong recovery (or fade) between morning and the rest of the day. */
function detectRecoveryArc(dayTasks: Task[]): Insight | null {
  const morning = dayTasks.filter((t) => dayjs(t.startTime).hour() < 12);
  const rest    = dayTasks.filter((t) => dayjs(t.startTime).hour() >= 12);
  if (morning.length < 2 || rest.length < 2) return null;

  const rMorning = completionRate(morning);
  const rRest    = completionRate(rest);

  if (rMorning < 0.5 && rRest > 0.8) {
    return {
      id: 'recovery-arc',
      text: `A slow start didn't define the day — you closed out ${Math.round(rRest * 100)}% of what was left after midday.`,
      significance: 0.7,
    };
  }
  if (rMorning > 0.8 && rRest < 0.5) {
    return {
      id: 'fade-arc',
      text: `Strong start, but momentum faded — only ${Math.round(rRest * 100)}% completed after midday versus ${Math.round(rMorning * 100)}% before.`,
      significance: 0.7,
    };
  }
  return null;
}

function groupByDate(tasks: Task[]): Map<string, Task[]> {
  const map = new Map<string, Task[]>();
  for (const t of tasks) {
    const d = dayjs(t.startTime).format('YYYY-MM-DD');
    if (!map.has(d)) map.set(d, []);
    map.get(d)!.push(t);
  }
  return map;
}

/** Week-only: finds the strongest and weakest day, if the spread is meaningful. */
function detectBestWeakestDay(weekTasks: Task[]): Insight | null {
  const byDate = groupByDate(weekTasks);
  if (byDate.size < 3) return null;

  const rates = [...byDate.entries()]
    .map(([date, tasks]) => ({ date, rate: completionRate(tasks) }))
    .sort((a, b) => b.rate - a.rate);
  const best  = rates[0];
  const worst = rates[rates.length - 1];
  if (best.rate - worst.rate < 0.3) return null;

  return {
    id: 'best-weakest-day',
    text: `${dayjs(best.date).format('dddd')} was the strongest day (${Math.round(best.rate * 100)}%); ${dayjs(worst.date).format('dddd')} was the weakest (${Math.round(worst.rate * 100)}%).`,
    significance: Math.min(best.rate - worst.rate, 0.85),
  };
}

/** Week-only: compares this week's completion rate to last week's. */
function detectWeekOverWeekTrend(weekTasks: Task[], prevWeekTasks: Task[]): Insight | null {
  if (prevWeekTasks.length < 3) return null;
  const rThis = completionRate(weekTasks);
  const rPrev = completionRate(prevWeekTasks);
  const delta = rThis - rPrev;
  if (Math.abs(delta) < 0.1) return null;

  const direction = delta > 0 ? 'up' : 'down';
  return {
    id: 'week-trend',
    text: `Completion is ${direction} ${Math.round(Math.abs(delta) * 100)} points versus last week.`,
    significance: Math.min(Math.abs(delta), 0.8),
  };
}

// ── Headlines ────────────────────────────────────────────────────────

function bucketLabel(rate: number, labels: [string, string, string, string]): string {
  if (rate >= 0.85) return labels[0];
  if (rate >= 0.6)  return labels[1];
  if (rate >= 0.4)  return labels[2];
  return labels[3];
}

function buildHeadline(rate: number, topInsight: Insight | null, labels: [string, string, string, string]): string {
  const bucket = bucketLabel(rate, labels);
  if (!topInsight) return `${bucket}.`;
  const clause = topInsight.text.split(/[;—]/)[0].replace(/\.$/, '').toLowerCase();
  return `${bucket} — ${clause}.`;
}

// ── Public entry points ──────────────────────────────────────────────

/**
 * dayTasks: the report date's own tasks.
 * baselineTasks: trailing ~30 days, used for comparison and recurring-skip
 * detectors. Both are bounded date-range fetches — see §5.3.
 */
export function computeDailyAnalysis(dayTasks: Task[], baselineTasks: Task[]): AnalysisResult {
  const candidates = [
    detectTimeOfDaySplit(dayTasks),
    detectBaselineComparison(dayTasks, baselineTasks),
    detectRecoveryArc(dayTasks),
    detectRecurringSkip(baselineTasks),
  ].filter((i): i is Insight => i !== null);

  const ranked = candidates.sort((a, b) => b.significance - a.significance);
  return {
    headline: buildHeadline(completionRate(dayTasks), ranked[0] ?? null,
      ['Excellent day', 'Solid day', 'Mixed day', 'Rough day']),
    insights: ranked.slice(0, 3),
  };
}

/**
 * weekTasks: the report week's own tasks (Sun–today, per §6's anchor).
 * prevWeekTasks: the immediately preceding calendar week, same bounds.
 */
export function computeWeeklyAnalysis(weekTasks: Task[], prevWeekTasks: Task[]): AnalysisResult {
  const candidates = [
    detectBestWeakestDay(weekTasks),
    detectWeekOverWeekTrend(weekTasks, prevWeekTasks),
    detectTimeOfDaySplit(weekTasks),
    detectRecurringSkip(weekTasks),
  ].filter((i): i is Insight => i !== null);

  const ranked = candidates.sort((a, b) => b.significance - a.significance);
  return {
    headline: buildHeadline(completionRate(weekTasks), ranked[0] ?? null,
      ['Strong week', 'Solid week', 'Uneven week', 'Tough week']),
    insights: ranked.slice(0, 4),
  };
}
```

**Deferred detectors (do not implement now):** an overrun-clustering detector
("tasks under 30 min overran 4 of 5 times") and a standalone-block-timing
detector ("three emergency blocks this week, all between 9–11 PM") were
considered and are worth adding later. Both need data this app doesn't
currently persist in a queryable form — confirmed overrun deltas at
completion time, and a timestamped log of standalone-block activations.
Adding either is a self-contained follow-up (one new small table, one new
detector function) and doesn't touch this engine's structure. Leave both
out of this pass rather than adding new logging infrastructure alongside
everything else in this plan.

---

### 5.2  DB schema and helpers — optional notes only

In `src/data/database.ts`, add to the schema creation block:

```sql
-- Optional freeform note the user can leave on a day or week's report.
-- No minimum length is enforced anywhere — an empty note is simply never
-- written (see dbSaveReportNote below).
CREATE TABLE IF NOT EXISTS report_notes (
  ref_date   TEXT NOT NULL,   -- day: YYYY-MM-DD; week: YYYY-MM-DD of week start
  type       TEXT NOT NULL CHECK(type IN ('day', 'week')),
  note       TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (ref_date, type)
);
```

Add to `dbPruneOldData()`:

```typescript
await database.runAsync(
  `DELETE FROM report_notes WHERE type = 'day' AND ref_date < ?`,
  [dayjs().subtract(8, 'day').format('YYYY-MM-DD')],
);
await database.runAsync(
  `DELETE FROM report_notes WHERE type = 'week' AND ref_date < ?`,
  [dayjs().subtract(14, 'day').format('YYYY-MM-DD')],
);
```

Helper functions:

```typescript
/** No-ops on an empty/whitespace note — there is nothing to "skip". */
export async function dbSaveReportNote(
  refDate: string,
  type: 'day' | 'week',
  note: string,
): Promise<void> {
  const trimmed = note.trim();
  if (!trimmed) return;
  return runWithDb('dbSaveReportNote', async (db) => {
    await db.runAsync(
      `INSERT INTO report_notes (ref_date, type, note, updated_at)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(ref_date, type) DO UPDATE
         SET note = excluded.note, updated_at = excluded.updated_at`,
      [refDate, type, trimmed, new Date().toISOString()],
    );
  });
}

export async function dbGetReportNote(refDate: string, type: 'day' | 'week'): Promise<string | null> {
  return runWithDbOr('dbGetReportNote', null, async (db) => {
    const row = await db.getFirstAsync<{ note: string }>(
      `SELECT note FROM report_notes WHERE ref_date = ? AND type = ?`, [refDate, type],
    );
    return row?.note ?? null;
  });
}

/** Returns any daily notes within a calendar week, keyed by date. Used to
 *  auto-surface them in the week view — see §5.3. */
export async function dbGetWeekReportNotes(sundayDate: string): Promise<Record<string, string>> {
  return runWithDbOr('dbGetWeekReportNotes', {}, async (db) => {
    const rows = await db.getAllAsync<{ ref_date: string; note: string }>(
      `SELECT ref_date, note FROM report_notes
       WHERE type = 'day' AND ref_date >= ? AND ref_date <= date(?, '+6 days')`,
      [sundayDate, sundayDate],
    );
    return Object.fromEntries(rows.map((r) => [r.ref_date, r.note]));
  });
}
```

---

### 5.3  Report screen — `app/report.tsx`

New screen. Expo Router params: `type: 'day' | 'week'`, `refDate: string`.

**Data fetched on mount** — every query is a bounded date range using the
existing `dbGetTasksInDateRange`, plus the two tiny note lookups from §5.2:

| param | queries |
|---|---|
| `type='day'` | `dbGetTasksInDateRange(startOfDay, endOfDay)` (report day), `dbGetTasksInDateRange(day-30, day-1)` (baseline for comparison detectors), `dbGetReportNote(refDate, 'day')` |
| `type='week'` | `dbGetTasksInDateRange(weekStart, weekEnd)`, `dbGetTasksInDateRange(prevWeekStart, prevWeekEnd)`, `dbGetWeekReportNotes(refDate)`, `dbGetReportNote(refDate, 'week')` |

Run them in `Promise.all` on mount. Compute the analysis with a `useMemo`:

```typescript
const analysis = useMemo(
  () => type === 'day'
    ? computeDailyAnalysis(dayTasks, baselineTasks)
    : computeWeeklyAnalysis(weekTasks, prevWeekTasks),
  [type, dayTasks, baselineTasks, weekTasks, prevWeekTasks],
);
```

**Day view layout (top → bottom):**

1. **Header** — back button + `"Yesterday's Report"`. No streak chip — there
   is no completion action left to track.

2. **Analysis section — top billing, right under the header:**
   ```tsx
   <Text style={styles.headline}>{analysis.headline}</Text>
   {analysis.insights.map((ins) => (
     <Text key={ins.id} style={styles.insightText}>{ins.text}</Text>
   ))}
   {analysis.insights.length === 0 && (
     <Text style={styles.insightText}>
       Not enough variation today to call out a specific pattern — steady as it goes.
     </Text>
   )}
   ```
   Rendered as plain paragraphs, not bullets or cards — it should read like
   something written, not a stats widget. This is the first thing the user
   sees after the header.

3. **Stats summary card** — completion rate, total tasks, focus time, skipped
   count. Supporting numbers, placed after the narrative rather than before it.

4. **Task breakdown** — flat list sorted by `startTime`. Status icon
   (✓ / ✗ / →) + task name + scheduled duration. Unchanged from before.

5. **Optional note** — the only writable field on the screen:
   ```tsx
   <Text style={[styles.noteLabel, { color: theme.muted }]}>Add a note (optional)</Text>
   <TextInput
     multiline
     placeholder="Anything you want to remember about today…"
     placeholderTextColor={theme.muted}
     value={note}
     onChangeText={setNote}
     onBlur={() => { void dbSaveReportNote(refDate, 'day', note); }}
     style={[styles.noteInput, { color: theme.text, borderColor: theme.border }]}
   />
   ```
   Saves silently on blur. `dbSaveReportNote` already no-ops on an empty
   string, so there is no separate "skip" control needed — leaving it blank
   simply does nothing.

**Week view layout (top → bottom):**

1. **Header** — `"Week of Mon dd – Sun dd"`. No streak chip.

2. **Analysis section** — same pattern as the day view, using
   `analysis.headline` / `analysis.insights` from `computeWeeklyAnalysis`
   (up to 4 insights instead of 3).

3. **Week summary card** — total tasks, completion rate, best day, total
   focus time.

4. **Day-by-day timeline** — one row per day (week start → today):
   - Day name + completion rate bar + focus mins
   - If `weekNotes[date]` exists (from `dbGetWeekReportNotes`), show it
     indented below that row in a muted, italic style. This is the auto-sync:
     a note left on an individual day surfaces here automatically — nothing
     to re-enter. The week's own insights (step 2) are independently
     generated from the week's aggregate data, so a pattern that was true on
     Tuesday naturally shows up in the week's narrative too, without the user
     describing it twice.

5. **Optional weekly note** — identical pattern to the day view's note field,
   scoped to `type: 'week'`. One note for the whole week, separate from any
   per-day notes.

---

### 5.4  Stats screen entry points — `app/(tabs)/stats.tsx`

**Yesterday view — plain entry row, no gamification:**

```tsx
<TouchableOpacity
  style={[styles.reportRow, { borderColor: theme.border, backgroundColor: theme.surface }]}
  onPress={() => router.push({ pathname: '/report', params: { type: 'day', refDate: yesterday } })}
>
  <Text style={{ color: theme.text }}>Yesterday's Report</Text>
  <Ionicons name="chevron-forward" size={16} color={theme.muted} />
</TouchableOpacity>
```

Always available, always current — there's no "unreviewed" state to flag
since the report generates itself. No streak, no pulse animation.

**Week view — locked insights card, unlock condition changed:**

The previous design gated this panel on "3 daily reviews written," which no
longer applies now that there's nothing to write. Recalibrated to a
data-availability threshold, computed from `weeklyDays` (§6) — zero new
queries:

```tsx
const daysWithDataThisWeek = weeklyDays.filter((d) => d.total > 0).length;
```

```tsx
{daysWithDataThisWeek >= 3 ? (
  <InsightsPanel tasks={weeklyTasks} />
) : (
  <View style={[styles.lockedCard, { borderColor: theme.border }]}>
    <Ionicons name="lock-closed" size={18} color={theme.muted} />
    <Text style={{ color: theme.muted }}>
      Productivity Insights — unlocks once {3 - daysWithDataThisWeek} more day
      {3 - daysWithDataThisWeek === 1 ? '' : 's'} of tasks are logged
    </Text>
  </View>
)}
```

This card is a plain `View`, not tappable — there's nothing to go "do" to
unlock it faster; it fills in passively as the week progresses. This is the
one gamification element carried over from the earlier design, and stays at
this single locked-panel level rather than adding anything further.

**`InsightsPanel`** (`src/components/InsightsPanel.tsx`) is a separate,
smaller thing from the insight engine in §5.1 — a fixed 3-line summary on
the stats screen itself, not the report's generated narrative. Keep both;
don't try to unify them. Receives `tasks: Task[]` for the current week:

- **Best focus hour** — group completed tasks by `startTime` hour.
  `"Your sharpest hour: 10 AM"`
- **Strongest day** — day with highest completion rate. `"Best day: Tuesday"`
- **Completion trend** — this week's rate vs last week's (from
  `historicalTasks`, already in memory). `"↑ 12% vs last week"` or `"↓ 8%"`

All three derived from `tasks` already in memory — zero extra DB queries.

---

## §6  Week stats — calendar anchor

**Problem:** `weeklyDays` is a rolling 7-day window. It shifts daily and
never aligns to calendar weeks.

### 6.1  Add `weekStartDay` to settings — `src/data/types.ts`

```typescript
export interface AppSettings {
  // ... existing fields ...
  weekStartDay: number;   // 0 = Sunday (default), 1 = Monday, …, 6 = Saturday
}
```

Add `weekStartDay: 0` to `DEFAULT_SETTINGS`. This makes the value
configurable later (one profile setting row) without any architectural change.
Do not add the profile UI now.

### 6.2  Week date utility — `src/utils/weekUtils.ts`

New file.

```typescript
import dayjs, { Dayjs } from 'dayjs';

/** Returns the most recent weekday matching startDay (0=Sun … 6=Sat). */
export function getWeekStart(startDay = 0): Dayjs {
  const today = dayjs();
  const diff  = (today.day() - startDay + 7) % 7;
  return today.subtract(diff, 'day').startOf('day');
}

export function getWeekEnd(weekStart: Dayjs): Dayjs {
  return weekStart.add(6, 'day').endOf('day');
}
```

### 6.3  Fix `weeklyDays` memo — `app/(tabs)/stats.tsx`

Replace the existing `weeklyDays` memo. Read `weekStartDay` from
`state.settings.weekStartDay ?? 0`.

```typescript
const weeklyDays = useMemo<WeekDay[]>(() => {
  const weekStart   = getWeekStart(weekStartDay);
  const today       = dayjs();
  const daysElapsed = today.diff(weekStart, 'day');   // 0–6

  return Array.from({ length: daysElapsed + 1 }, (_, i) => {
    const d        = weekStart.add(i, 'day');
    const dStr     = d.format('YYYY-MM-DD');
    const dayTasks = historicalTasks.filter(
      (t) => dayjs(t.startTime).format('YYYY-MM-DD') === dStr,
    );
    const done = dayTasks.filter((t) => t.status === 'completed');
    return {
      day:          d.format('ddd'),
      date:         dStr,
      isToday:      d.isSame(today, 'day'),
      total:        dayTasks.length,
      completed:    done.length,
      focusMinutes: done
        .filter((t) => t.focusMode)
        .reduce((s, t) => s + t.durationMinutes, 0),
    };
  });
}, [historicalTasks, weekStartDay]);
```

Add a subtitle below the filter pills:

```tsx
{filter === 'week' && (
  <Text style={[styles.weekSubtitle, { color: theme.muted }]}>
    {`${getWeekStart(weekStartDay).format('MMM D')} – ${getWeekEnd(getWeekStart(weekStartDay)).format('MMM D')}`}
  </Text>
)}
```

When `weeklyDays.length === 1` (today IS the start day — week just began):

```tsx
{filter === 'week' && weeklyDays.length === 1 && (
  <Text style={[styles.weekNote, { color: theme.muted }]}>
    Week just started — check back tomorrow for trends.
  </Text>
)}
```

No new DB queries. The existing 30-day `historicalTasks` fetch covers
any anchor day.

---

## §7  Focus session lifecycle — orphaned sessions, stuck toggles, no recovery path

**Context:** live debugging surfaced a cluster of related bugs, confirmed
against the actual running app across two separate incidents — one triggered
by "Clear All Tasks," and a second, unrelated recurrence during completely
normal use (create task → skip/complete it). The second incident is the
important data point: it rules out "Clear All Tasks" as the sole cause and
points to an **intermittent** native-call hang that can occur on any call to
`stopFocusMode()`, not a single deterministic trigger. Four fixes below
address this: one bounds the damage from any single hang, one closes a
separate deterministic logic gap, one removes a dead-end in the UI, and one
adds a self-healing safety net so a future occurrence — however it's
triggered — can't leave the app stuck until a device reboot.

**How this was found:** `app/active.tsx`'s status card renders `focusActive`
(from `state.focusSession?.isActive`) independently of whether the linked
task (`focusTask`) can still be found. A screenshot showed the "Active" badge
lit with the body reading "No task-based focus session is running" —
which only happens when `focusActive` is `true` but `focusTask` resolves to
nothing. `always-on.tsx` and `block-defense.tsx` both gate their "cannot
remove while protected" refusal on `state.focusSession?.isActive === true`
directly, with no task lookup at all — so once the flag is stuck, every
screen that checks it agrees the app is "protected," while the one screen
that's supposed to show *why* falls back to a misleadingly empty state.

Critically, `state.focusSession` is rehydrated from a **persisted** SharedPrefs
flag on every cold start. A device reboot clears in-memory/process-level
hangs, but does nothing for a SharedPrefs value that was never rewritten to
`false` — which is exactly what happens if `stopFocusMode()` hangs before
reaching its `publishFocusSnapshot(false, ...)` call.

---

### 7.1  Timeout-guard every native call in `stopFocusMode()` — and fix the deeper one

Two layers need this fix, not one. `AppContext.tsx`'s `stopFocusMode()`
wraps `_stopFocusMode()` (imported from `src/services/focusService.ts`) as
its first step — but that inner function has its **own** sequence of native
calls, and tracing the 36h focus-time bug (§7.5) led straight back to it:

```typescript
// focusService.ts — current order
export async function stopFocusMode(pinHash: string | null = null): Promise<void> {
  // ...
  await ForegroundServiceModule.stopService(pinHash).catch(() => {});
  await SharedPrefsModule.publishFocusSnapshot(false, null, null, 0, null, [], null, pinHash).catch(() => {});

  if (hadActiveSession && task) {
    await dbEndFocusSession(task.id);   // ← closes the focus_sessions row — but only reached if both calls above complete
    await dismissPersistentNotification();
  }
}
```

`.catch(() => {})` does nothing for a call that hangs rather than rejects —
so if `ForegroundServiceModule.stopService` ever stalls, `dbEndFocusSession`
is never reached, and that task's `focus_sessions` row keeps `ended_at`
`NULL` forever. That's the exact mechanism behind §7.5.

**Fix: move the DB close to the front, and timeout-wrap what's left.** Add
a shared helper (used by both this file and AppContext.tsx — extract it to
`src/utils/withTimeout.ts` rather than duplicating it):

```typescript
// src/utils/withTimeout.ts
export function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
  return Promise.race([
    promise,
    new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error(`${label} timed out after ${ms}ms`)), ms),
    ),
  ]);
}
```

```typescript
// focusService.ts — fixed order
export async function stopFocusMode(pinHash: string | null = null): Promise<void> {
  const hadActiveSession = focusActive && currentTask !== null;
  focusActive = false;
  const task = currentTask;
  currentTask = null;
  onFocusViolation = null;
  appStateSubscription?.remove();
  appStateSubscription = null;

  // Close the DB row FIRST — before any native call that could hang. This
  // is the actual fix for §7.5: whatever else stalls below, the session's
  // ended_at is already correct, so dbGetTodayFocusMinutes() never again
  // treats an abandoned session as still running for the rest of the day.
  if (hadActiveSession && task) {
    await dbEndFocusSession(task.id).catch(() => {});
  }

  await withTimeout(ForegroundServiceModule.stopService(pinHash), 5000, 'ForegroundServiceModule.stopService').catch(() => {});
  await withTimeout(SharedPrefsModule.publishFocusSnapshot(false, null, null, 0, null, [], null, pinHash), 5000, 'publishFocusSnapshot').catch(() => {});

  if (hadActiveSession && task) {
    await dismissPersistentNotification().catch(() => {});
  }
}
```

A brief window where the DB says "session ended" while the native blocking
service is still catching up (at most a few seconds, now bounded) is a fine
trade against the current failure mode, where an abandoned row can silently
inflate a stat by dozens of hours.

**The outer `AppContext.tsx` wrapper still needs its own timeout-guarding**
too — it makes its own redundant native calls after `_stopFocusMode()`
returns (`ForegroundServiceModule.stopService`, `SharedPrefsModule.publishFocusSnapshot`
again, plus `NetworkBlockModule.stopNetworkBlock`), and none of those should
be allowed to block the final dispatch either. Use the same shared
`withTimeout` from `src/utils/withTimeout.ts` rather than a second local copy:

```typescript
const stopFocusMode = useCallback(async (pinHash: string | null = null) => {
  const dismissedTaskId = stateRef.current.focusSession?.taskId ?? null;

  try {
    await withTimeout(_stopFocusMode(pinHash), 5000, '_stopFocusMode');
  } catch (e) {
    void logger.warn('AppContext', `stopFocusMode JS-layer failed: ${String(e)}`);
  }
  try {
    await withTimeout(ForegroundServiceModule.stopService(), 5000, 'ForegroundServiceModule.stopService');
  } catch (e) {
    void logger.warn('AppContext', `stopService failed: ${String(e)}`);
  }
  try {
    await withTimeout(
      SharedPrefsModule.publishFocusSnapshot(false, null, null, 0, null, [], null, pinHash),
      5000,
      'publishFocusSnapshot',
    );
  } catch (e) {
    void logger.warn('AppContext', `publishFocusSnapshot failed: ${String(e)}`);
  }
  try {
    await withTimeout(NetworkBlockModule.stopNetworkBlock(pinHash), 5000, 'stopNetworkBlock');
  } catch (e) {
    void logger.warn('AppContext', `stopNetworkBlock failed: ${String(e)}`);
  }

  // Persist the dismissal so tryAutoStartFocus doesn't immediately restart
  // this same task occurrence — see §7.2.
  if (dismissedTaskId) {
    dismissedFocusTaskIdRef.current = dismissedTaskId;
    void SharedPrefsModule.putString('focus_dismissed_task_id', dismissedTaskId).catch(() => {});
  }

  dispatch({ type: 'SET_FOCUS_SESSION', payload: null });
}, []);
```

Keep the four calls sequential, as they already are — this is a surgical
addition, not a restructure. The point isn't speed, it's that every branch
now reaches the final dispatch within a bounded time no matter what hangs.

`SharedPrefsModule.putString`/`getString` are already used generically
elsewhere in the codebase (`defense_pin_hash` in `keyword-blocker.tsx`,
`vpn-block-list.tsx`, `password-protection.tsx`) — no new native method is
needed for the dismissal key in §7.2.

---

### 7.2  Auto-start must remember an explicit stop

`src/context/AppContext.tsx`. `tryAutoStartFocus()` has no memory of "the
user just stopped this." Stopping focus clears `state.focusSession` to
`null` — which is precisely the condition `tryAutoStartFocus` needs to
restart a session for the same task, since stopping doesn't change the
task's status or its scheduled window. This fires within 30 seconds if the
app stays foregrounded, or immediately on the next foreground resume if it
doesn't (JS timers throttle in the background).

Add one ref alongside the existing instance variables:

```typescript
const dismissedFocusTaskIdRef = useRef<string | null>(null);
```

Hydrate it in `init()`, alongside the other cold-start reads:

```typescript
dismissedFocusTaskIdRef.current =
  await SharedPrefsModule.getString('focus_dismissed_task_id').catch(() => null);
```

Add one line to `tryAutoStartFocus()`'s guard, after the existing
`active`/`active.focusMode` checks:

```typescript
if (!active || !active.focusMode) return;
if (active.id === dismissedFocusTaskIdRef.current) return;   // user explicitly stopped this occurrence
```

`stopFocusMode()` already sets and persists this ref (shown in §7.1). No
explicit cleanup is needed — once the dismissed task's window passes,
`getActiveTask()` never returns it again, so the check becomes permanently
irrelevant for that task; the single SharedPrefs key is simply overwritten
the next time any task is stopped.

This makes "Stop Focus" a genuine, permanent opt-out for that specific task
occurrence — consistent with how Skip already behaves, and distinct from
whatever "Emergency Override" is meant to be.

---

### 7.3  "Stop Focus" must not require a resolved task

`app/active.tsx` and `app/(tabs)/focus.tsx`. Both gate their Stop Focus
control behind `focusActive && focusTask`. If the linked task no longer
exists — orphaned by exactly the kind of stuck session described above —
the button doesn't render at all, and there is currently no other way in the
app to clear the session. Change the gate to render whenever `focusActive`
is true, with task-specific details shown only when `focusTask` resolves:

```tsx
{focusActive ? (
  <>
    {focusTask ? (
      <>{/* existing task name, end time, etc. */}</>
    ) : (
      <EmptyText text="A focus session is active, but its task could not be found." />
    )}
    <StopFocusButton onPress={handleStopFocus} />   {/* always rendered when focusActive */}
  </>
) : (
  <EmptyText text="No task-based focus session is running." />
)}
```

Apply the same change to both files — this closes the dead-end regardless of
what causes a session to become orphaned in the future.

---

### 7.4  Reconciliation — self-heal an orphaned session within 30 seconds

Given the confirmed recurrence through more than one path, add a general
safety net rather than only patching the specific triggers found so far.
Add alongside `tryAutoStartFocus` in `src/context/AppContext.tsx`:

```typescript
function shouldForceClearSession(s: AppState): boolean {
  const session = s.focusSession;
  if (!session?.isActive) return false;
  const linkedTask = s.tasks.find((t) => t.id === session.taskId);
  if (!linkedTask) return true;                                                    // orphaned
  if (linkedTask.status === 'completed' || linkedTask.status === 'skipped') return true;  // resolved but lingering
  const graceMs = 5 * 60 * 1000;   // allow a short legitimate overrun before acting
  if (Date.now() > new Date(linkedTask.endTime).getTime() + graceMs) return true;
  return false;
}
```

Call it from the existing 30-second tick, alongside the existing
`tryAutoStartFocus()` call:

```typescript
if (shouldForceClearSession(stateRef.current)) {
  void logger.warn('AppContext', 'tick: force-clearing an orphaned focus session');
  void stopFocusMode().catch((e) =>
    void logger.warn('AppContext', `reconcile stopFocusMode failed: ${String(e)}`));
}
```

Because `stopFocusMode()` is now timeout-guarded (§7.1), this check
guarantees that any future divergence between "session says active" and
reality — regardless of which new code path eventually causes it — self-heals
within 30 seconds instead of persisting until a device reboot.

---

### 7.5  Confirmed — inflated focus time, traced to its exact source

Root-caused. `app/(tabs)/stats.tsx` gets "Focus time today" from
`dbGetTodayFocusMinutes()` in `src/data/database.ts`:

```typescript
export async function dbGetTodayFocusMinutes(): Promise<number> {
  return runWithDb('dbGetTodayFocusMinutes', async (database) => {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const rows = await database.getAllAsync<{ started_at: string; ended_at: string | null }>(
      `SELECT started_at, ended_at FROM focus_sessions WHERE started_at >= ? ORDER BY id DESC`,
      [startOfDay.toISOString()],
    );
    let totalMs = 0;
    const now = Date.now();
    for (const row of rows) {
      const start = new Date(row.started_at).getTime();
      const end = row.ended_at ? new Date(row.ended_at).getTime() : now;   // ← the bug
      totalMs += Math.max(0, end - start);
    }
    return Math.floor(totalMs / 60000);
  });
}
```

For any row with `ended_at IS NULL`, this treats the session as still live
and counts `now − started_at` — correct for a genuinely active session, but
if a row was orphaned by the exact hang described in §7.1 (never reaching
`dbEndFocusSession`), it keeps contributing a larger and larger number every
time the stat is viewed, for the rest of that day. Chained skip/restart
testing (§7.2's pre-fix auto-restart bug, combined with §7.1's hang) could
leave several such rows open across one day — this is what produced 36h27m.

This also explains exactly why it reset cleanly the next day: the query
filters `started_at >= startOfDay`. Once a new calendar day begins, the
previous day's orphaned rows fall outside that window — they were never
actually fixed, they just stopped being counted. They'd still be sitting in
the DB with `ended_at IS NULL` (confirm this by inspecting `focus_sessions`
directly if you want to see the leftover rows before this fix lands).

**`dbGetAllTimeFocusMinutes()` was checked and does not share this bug** —
it filters `WHERE is_active = 0` and explicitly skips any row missing
`ended_at`, so orphaned rows are already excluded from the lifetime total.
No change needed there.

**Fix, two parts:**

1. §7.1's reordering (closing the DB row before any native call that can
   hang) stops new orphaned rows from being created going forward.
2. Add a defensive cap in `dbGetTodayFocusMinutes()` itself, so even a row
   that somehow still ends up orphaned in the future can't balloon the
   day's total past a sane ceiling:

```typescript
export async function dbGetTodayFocusMinutes(): Promise<number> {
  return runWithDb('dbGetTodayFocusMinutes', async (database) => {
    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const rows = await database.getAllAsync<{ started_at: string; ended_at: string | null }>(
      `SELECT started_at, ended_at FROM focus_sessions WHERE started_at >= ? ORDER BY id DESC`,
      [startOfDay.toISOString()],
    );
    let totalMs = 0;
    const now = Date.now();
    // A single continuous session longer than this is implausible — cap it
    // so one stuck row can never again inflate the day's total into
    // something impossible, even if some future bug reopens this path.
    const MAX_SESSION_MS = 6 * 60 * 60 * 1000; // 6 hours — adjust if your longest real sessions run longer
    for (const row of rows) {
      const start = new Date(row.started_at).getTime();
      const end = row.ended_at ? new Date(row.ended_at).getTime() : now;
      const elapsed = Math.max(0, end - start);
      totalMs += Math.min(elapsed, MAX_SESSION_MS);
    }
    return Math.floor(totalMs / 60000);
  });
}
```

This is deliberately layered with §7.1 rather than relying on either fix
alone — §7.1 stops the cause, this stops the symptom from ever being
displayable again even if some other path reintroduces it.

---

**Dark mode:**
- [ ] `TaskCard.tsx` has `useTheme()` — no `COLORS.*` in any `style` prop
- [ ] `AppPickerSheet.tsx` has `useTheme()` — no `COLORS.*` in colour positions
- [ ] Focus tab SafeAreaView uses `'0D'` tint in dark mode, `'18'` in light

**File association:**
- [ ] `NativeFilePickerModule.kt` has `readUri(uriString, promise)` method
- [ ] `NativeFilePickerModule.ts` exposes `readUri(uri): Promise<string | null>`
- [ ] Intent filters are in `manifest_additions.xml` and patched by `install.sh`
- [ ] `app/_layout.tsx` calls `Linking.getInitialURL()` on mount and subscribes to `url` events
- [ ] `app/import-confirm.tsx` exists and calls `restoreFromJson` on confirm
- [ ] Non-`.focusflow` content URIs are rejected gracefully (the `parseBackupJson` guard handles this)

**Group schedule VPN:**
- [ ] `GreyoutWindow` type has `vpnEnabled?: boolean`
- [ ] `GreyoutScheduleModal` has `vpnEnabled` state seeded from the existing window on edit
- [ ] The VPN toggle row renders in the form and its value is saved into the window
- [ ] Schedule → GreyoutWindow conversion preserves `vpnEnabled`
- [ ] GreyoutWindow → RecurringBlockSchedule conversion preserves `vpnEnabled`
- [ ] `SharedPrefsModule.kt` has `publishScheduleVpnSnapshot(packagesJson, promise)`
- [ ] `SharedPrefsModule.ts` has the matching `publishScheduleVpnSnapshot` wrapper
- [ ] `syncScheduleVpn()` exists in AppContext and is called from init, foreground resume, and tick
- [ ] `lastScheduleVpnRef` prevents redundant SharedPrefs writes when nothing changed
- [ ] `VpnPolicyCoordinator.kt` reads `net_block_schedule_vpn_pkgs` in `effectivePolicy()`

**Standalone block VPN:**
- [ ] `publishStandaloneSnapshot` in `SharedPrefsModule.kt` accepts `vpnPackages: ReadableArray?` as 5th param
- [ ] Active branch writes `"net_block_standalone_vpn_packages"` from `vpnPackages`
- [ ] Inactive branch writes `"net_block_standalone_vpn_packages"` as `"[]"`
- [ ] `SharedPrefsModule.ts` wrapper passes `vpnPackages` as 5th arg to native call
- [ ] `setStandaloneBlockAndAllowance` in `AppContext.tsx` passes `resolvedVpnPackages` to `publishStandaloneSnapshot`

**Auto-generated report:**
- [ ] `src/services/insightEngine.ts` exists, pure functions only, no DB access
- [ ] `computeDailyAnalysis` runs all 4 day-scoped detectors and returns top 3 by significance
- [ ] `computeWeeklyAnalysis` runs all 4 week-scoped detectors and returns top 4 by significance
- [ ] Each detector returns `null` (not a placeholder insight) when its data threshold isn't met
- [ ] Overrun-clustering and standalone-block-timing detectors are explicitly NOT implemented this pass
- [ ] `report_notes` table exists; `dbSaveReportNote` no-ops on empty/whitespace input
- [ ] Pruner deletes day notes older than 8 days, week notes older than 14 days
- [ ] `dbGetReportNote`, `dbGetWeekReportNotes` implemented
- [ ] `app/report.tsx` fetches bounded date ranges only — no full-table scans
- [ ] Analysis section (headline + insights) renders directly under the header, above the stats card
- [ ] Optional note field saves on blur; no minimum length, no submit button, no gate
- [ ] Week view surfaces daily notes inline automatically via `dbGetWeekReportNotes` — no re-entry
- [ ] Stats screen entry row has no streak, no pulse animation, no "unreviewed" state
- [ ] Locked insights card unlock condition uses `weeklyDays` data-availability (§6), not review count
- [ ] Locked insights card is non-tappable (`View`, not `TouchableOpacity`)
- [ ] `InsightsPanel` (stats-screen summary) kept separate from `insightEngine.ts` (report narrative) — not merged

**Focus session lifecycle:**
- [ ] `src/utils/withTimeout.ts` created — single shared helper, not duplicated per file
- [ ] `focusService.ts`'s `stopFocusMode()` calls `dbEndFocusSession(task.id)` FIRST, before `ForegroundServiceModule.stopService`/`SharedPrefsModule.publishFocusSnapshot`
- [ ] Those two native calls inside `focusService.ts` are wrapped with `withTimeout` (5000ms)
- [ ] `AppContext.tsx`'s `stopFocusMode()` wraps all four of its own native calls with the same shared `withTimeout`
- [ ] `AppContext.tsx`'s final `dispatch({ type: 'SET_FOCUS_SESSION', payload: null })` is unconditional — reached even if every timeout fires
- [ ] `dismissedFocusTaskIdRef` added; hydrated from `SharedPrefsModule.getString('focus_dismissed_task_id')` in `init()`
- [ ] `stopFocusMode()` sets the ref and persists the dismissed taskId before clearing session state
- [ ] `tryAutoStartFocus()` returns early when `active.id === dismissedFocusTaskIdRef.current`
- [ ] `app/active.tsx`'s Stop Focus button renders whenever `focusActive` is true, independent of `focusTask` resolving
- [ ] Same button-visibility fix applied to `app/(tabs)/focus.tsx`
- [ ] `shouldForceClearSession()` added and called from the 30-second tick, alongside `tryAutoStartFocus()`
- [ ] Reconciliation uses a 5-minute grace period past `endTime` before force-clearing
- [ ] `dbGetTodayFocusMinutes()` caps any single row's contribution at `MAX_SESSION_MS` (6h default)
- [ ] `dbGetAllTimeFocusMinutes()` confirmed unaffected (already filters `is_active = 0` and skips null `ended_at`) — no change made there

**Week anchor:**
- [ ] `weekStartDay: 0` added to `AppSettings` and `DEFAULT_SETTINGS`
- [ ] `src/utils/weekUtils.ts` exists with `getWeekStart(startDay)` and `getWeekEnd(weekStart)`
- [ ] `weeklyDays` reads `weekStartDay` from settings and uses `getWeekStart()`
- [ ] Week subtitle shows anchored date range using `weekUtils`
- [ ] Single-day note shown when today is the week start day
- [ ] No new DB queries; existing `historicalTasks` fetch covers the range

---

## Priority order

1. **§7 — Focus session lifecycle** (highest severity — currently blocking normal use; implement in order: 7.1 timeout-guard → 7.2 dismissal memory → 7.3 button visibility → 7.4 reconciliation)
2. **§1 — Dark mode** (quickest, highest user-visibility, no dependencies)
3. **§6 — Week anchor** (`weekUtils.ts` + one memo change; implement first since §5 depends on `getWeekStart`)
4. **§4 — Standalone VPN bug** (3-file fix; self-contained)
5. **§5 — Auto-generated report** (implement in order: `insightEngine.ts` → `report_notes` table/helpers → report screen → stats entry points)
6. **§3 — Group schedule VPN** (most files, but each change is small)
7. **§2 — File association** (native + manifest; lowest daily-use frequency)
