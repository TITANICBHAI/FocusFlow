# Focus Tab Redesign — Implementation Prompt

You are rewriting `app/(tabs)/focus.tsx`.

You have two source files:
- **FILE A** — the OLD `focus.tsx` (from the original FocusFlow codebase) — contains the standalone block UI that was well-designed when it had the full screen to itself
- **FILE B** — the NEW `focus.tsx` (current codebase) — contains all updated logic, new hook signatures, new component imports, and the new task panel UI, but has a broken split layout

**Your job:** Keep everything from FILE B (all logic, all hooks, all handlers, all modals, all sub-components, all styles) and redesign only the JSX layout section — replacing the current split/splitTop/splitBottom architecture with a cleaner system described below.

Do NOT change any logic. Do NOT remove any handler. Do NOT remove any hook. Do NOT remove any state variable. Do NOT remove any sub-component (StandaloneCountdown, TimerDisplay, PomodoroStrip, SecondaryBtn, QuickPresetStrip — keep them all exactly as they are in FILE B). Only the layout rendering changes.

---

## The Core Problem With the Current Code

```
const splitMode = standaloneActive || isFocusing;  ← this is wrong
```

`splitMode` activates when focus is running even with NO standalone block, forcing a 43%/57% split that shows a useless "No standalone block running" half. The split is always cramped. The standalone section inside split mode has smaller buttons, fewer time options, and an inconsistent set (+1h/+2h/+4h/+8h vs the full view's +30m/+1h/+2h/+4h). Remove `splitMode` entirely. Replace it with the state machine below.

---

## New State Machine — Four Clean States

Compute these two booleans at the top of the render (exactly as FILE B already does):

```typescript
const standaloneActive = Boolean(
  settings.standaloneBlockUntil &&
    (settings.standaloneBlockPackages ?? []).length > 0 &&
    new Date(settings.standaloneBlockUntil).getTime() > Date.now(),
);
const isFocusing = state.focusSession !== null && state.focusSession.isActive;
```

Then derive four mutually exclusive states:

| State | Condition | What to render |
|-------|-----------|----------------|
| **A** | `!task && !standaloneActive` | Empty / idle state — no task, no block |
| **B** | `!task && standaloneActive` | Full-screen standalone block view |
| **C** | `task && !standaloneActive` | Full-screen task + focus view |
| **D** | `task && standaloneActive` | Tab switcher: [Focus Task] [Block Active] |

Add one new state variable:
```typescript
const [activeTab, setActiveTab] = useState<'task' | 'block'>('task');
```

Add one useEffect to reset the tab when the task disappears:
```typescript
useEffect(() => {
  if (!task) setActiveTab('task');
}, [!!task]);
```

---

## State A — Idle (no task, no block)

**Identical to FILE B's current `!task && !standaloneActive` branch.** Keep it exactly as-is. No changes needed here.

Content (top to bottom):
- Timer icon
- "Ready to focus?" title
- Subtitle
- "Open Schedule" primary button
- "Block Apps Without a Task" row button → opens StandaloneBlockModal
- QuickPresetStrip

---

## State B — Full-screen Standalone Block (no task)

Take the layout structure from FILE A's standalone-only screen (the `!task && standaloneActive` branch). Do NOT use FILE B's cramped 43% top panel. This is a full-screen ScrollView with proper breathing room.

Structure (inside a `ScrollView` that fills the screen):
```
[Large red ban icon in a circle — width:90 height:90 borderRadius:45]

"Apps Blocked"                     ← large title, theme.text
"Standalone block is running."     ← subtitle, theme.muted
"You can add more apps or extend the time, but cannot stop the block early."

[StandaloneCountdown component]    ← keep FILE B's exact component, full width

─── ADD TIME ───────────────────── ← section label, muted uppercase

[Row of 4 equal buttons]
  +30m    +1h    +2h    +4h
  (onPress: handleAddTime with 30, 60, 120, 240 minutes respectively)

[QuickPresetStrip]                 ← keep FILE B's exact component
  presets={blockPresets}
  active={true}
  onPress={(preset) => handleQuickBlock(preset, 1)}

[Outline button — full width]
  🔴 ban icon + "Add More Apps to Block"
  onPress: setBlockModalVisible(true)
```

**Style notes:**
- Time buttons: `flex:1`, `paddingVertical: SPACING.md`, `borderRadius: RADIUS.md`, `borderWidth:1`
- Active button bg: `COLORS.primary + '14'`, border: `COLORS.primary + '44'`, text: `COLORS.primary`, fontWeight: `'700'`
- Row container: `flexDirection:'row'`, `gap: SPACING.sm`, full width
- All content horizontally padded: `paddingHorizontal: SPACING.lg`
- Bottom padding: `60 + insets.bottom + 20` (same as task scroll)

---

## State C — Full-screen Task + Focus (task exists, no standalone)

**This is FILE B's task panel, unchanged.** Remove all references to `splitMode`, `splitBottom`, `splitTop`, `splitTaskContent`. The task ScrollView takes the full remaining screen. No split. No top block panel.

The content inside the ScrollView stays exactly as FILE B has it:
- Status row (green/gray dot + status text)
- Task panel card (timer, title, time range, progress bar, progress label)
- PomodoroStrip (if active + enabled)
- More active chip (if otherActiveCount > 0)
- "Time's up" prompt (if awaitingDecision)
- Tags row (if task.tags.length > 0)
- Action buttons (Activate Focus / Stop Focus, Done + Extend secondary row, Emergency Override)
- Allowed apps row (if focusing)

Bottom padding: `60 + insets.bottom + 20`.

---

## State D — Tab Switcher (task AND standalone both active)

This is the key new addition. When both coexist, show a tab bar at the top of the content area (below the defense hint banner, above the content).

### Tab Bar Component

```tsx
function FocusTabSwitcher({
  activeTab,
  onSwitch,
  blockUntilIso,
}: {
  activeTab: 'task' | 'block';
  onSwitch: (tab: 'task' | 'block') => void;
  blockUntilIso: string;
}) {
  // Live remaining time for block tab label
  const [remaining, setRemaining] = useState(0);
  useEffect(() => {
    const tick = () => setRemaining(Math.max(0, new Date(blockUntilIso).getTime() - Date.now()));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [blockUntilIso]);
  const hrs = Math.floor(remaining / 3600000);
  const mins = Math.floor((remaining % 3600000) / 60000);
  const secs = Math.floor((remaining % 60000) / 1000);
  const blockLabel = hrs > 0 ? `${hrs}h ${mins}m left` : `${mins}:${secs.toString().padStart(2, '0')} left`;

  return (
    <View style={switcherStyles.container}>
      <TouchableOpacity
        style={[switcherStyles.tab, activeTab === 'task' && switcherStyles.tabActive]}
        onPress={() => onSwitch('task')}
        activeOpacity={0.75}
      >
        <Ionicons name="shield-checkmark-outline" size={15}
          color={activeTab === 'task' ? COLORS.primary : theme.muted} />
        <Text style={[switcherStyles.tabText,
          activeTab === 'task' ? switcherStyles.tabTextActive : { color: theme.muted }]}>
          Focus Task
        </Text>
      </TouchableOpacity>
      <TouchableOpacity
        style={[switcherStyles.tab, activeTab === 'block' && switcherStyles.tabActiveBlock]}
        onPress={() => onSwitch('block')}
        activeOpacity={0.75}
      >
        <Ionicons name="ban" size={15}
          color={activeTab === 'block' ? COLORS.red : theme.muted} />
        <Text style={[switcherStyles.tabText,
          activeTab === 'block' ? switcherStyles.tabTextBlock : { color: theme.muted }]}>
          Block Active
        </Text>
        <View style={[switcherStyles.pill, { backgroundColor: COLORS.red + '20' }]}>
          <Text style={[switcherStyles.pillText, { color: COLORS.red }]}>{blockLabel}</Text>
        </View>
      </TouchableOpacity>
    </View>
  );
}

const switcherStyles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    marginHorizontal: SPACING.lg,
    marginTop: SPACING.sm,
    marginBottom: SPACING.xs,
    borderRadius: RADIUS.lg,
    borderWidth: 1,
    overflow: 'hidden',
  },
  tab: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: SPACING.xs,
    paddingVertical: SPACING.sm,
    paddingHorizontal: SPACING.xs,
  },
  tabActive: {
    backgroundColor: COLORS.primary + '18',
  },
  tabActiveBlock: {
    backgroundColor: COLORS.red + '12',
  },
  tabText: {
    fontSize: FONT.sm,
    fontWeight: '700',
  },
  tabTextActive: {
    color: COLORS.primary,
  },
  tabTextBlock: {
    color: COLORS.red,
  },
  pill: {
    borderRadius: RADIUS.full,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  pillText: {
    fontSize: 10,
    fontWeight: '700',
  },
});
```

The `theme` reference above should come from `useTheme()` in the parent and be passed in as a prop, or the component should call `useTheme()` itself.

### State D Layout

```tsx
{task && standaloneActive && (
  <>
    <FocusTabSwitcher
      activeTab={activeTab}
      onSwitch={setActiveTab}
      blockUntilIso={settings.standaloneBlockUntil!}
      theme={theme}
    />
    {activeTab === 'task'
      ? <TaskPanel ... />      ← the full State C task content
      : <StandalonePanel ... /> ← the full State B standalone content
    }
  </>
)}
```

When `activeTab === 'task'` — render the EXACT same full-screen task panel as State C. Add ONE extra element at the bottom of the actions section: a small chip indicating the block is running, so the user doesn't forget.

```tsx
{/* Below the action buttons in task tab of State D only */}
<TouchableOpacity
  style={[styles.standaloneChip, { marginTop: SPACING.sm }]}
  onPress={() => setActiveTab('block')}
  activeOpacity={0.8}
>
  <Ionicons name="ban" size={12} color={COLORS.red} />
  <Text style={styles.standaloneChipText}>
    Block running · {blockedCount} apps · tap to manage
  </Text>
</TouchableOpacity>
```

Where `blockedCount = (settings.standaloneBlockPackages ?? []).length`.

When `activeTab === 'block'` — render the EXACT same full-screen standalone panel as State B. No changes.

---

## Permission Banner

Keep FILE B's `hasAccessibilityPermission === false && !isFocusing` orange banner exactly as-is. Position: always renders between the header (defense hint) and the main content, regardless of which state is active. It is NOT inside the ScrollView — it is a fixed View above it.

---

## SafeAreaView Background Color

Keep FILE B's logic exactly:
```typescript
backgroundColor: task ? task.color + '18' : theme.background
```

---

## All Modals — Keep Exactly as FILE B

Keep in place after the main layout, before the closing `</SafeAreaView>`:
- `{blockModal}` — the StandaloneBlockModal
- `{task && <ExtendModal ... />}` — ExtendModal
- `<PinRotationModal ... />`
- `<PinVerifyModal ... />`

---

## Styles to ADD

Add `switcherStyles` (defined inline with `StyleSheet.create` in the `FocusTabSwitcher` component or at the bottom of the file). Keep all existing `styles`, `countdownStyles`, `timerStyles`, `pomStyles` from FILE B exactly as they are.

Remove the following styles that are no longer used once splitMode is gone:
- `splitShell`
- `splitTop`
- `splitBottom`
- `splitPanelContent`
- `splitTaskContent`
- `splitZoneHeader`
- `inactiveStandalonePanel`
- `inactiveSessionPanel`
- `splitPanelTitle`
- `splitPanelSubtitle`
- `splitQuickActions`
- `splitQuickButton`
- `splitQuickButtonText`
- `standaloneSplitCard`
- `standaloneSplitHeader`
- `standaloneSplitIcon`
- `standaloneSplitCopy`
- `standaloneSplitTitle`
- `standaloneSplitDescription`
- `splitOutlineAction`
- `splitAddTimeRow`
- `splitAddTimeBtn`

All other styles stay.

---

## Complete State/Case Coverage

Every case the user can encounter, and what they see:

| User situation | State | Tab visible? | What shows |
|---------------|-------|-------------|------------|
| App opened, no tasks today, never blocked | A | None | "Ready to focus?" + Block button + presets |
| App opened, no tasks, preset quick-blocked | A | None | Same — standaloneActive is false until setStandaloneBlockAndAllowance saves and re-renders |
| Block just activated via preset | B | None | Full standalone screen — countdown, buttons, presets |
| Block active, no task | B | None | Full standalone screen |
| Block expires while user is on screen | B→A | None | `standaloneActive` recalculates to false on next 30-second tick OR on re-render; screen transitions to State A |
| Task is active, no block, no focus | C | None | Full task panel — "Activate Focus" primary button |
| Task is active, focus started | C | None | Full task panel — "Stop Focus" primary button, emergency override, pomodoro if enabled |
| Task ended, awaiting decision, no block | C | None | Full task panel — "Time's up" prompt with Done/Extend/Skip |
| Task active, focus not started, then user quick-blocks | D | [Focus Task] [Block Active] | Tab switcher appears. Stays on Focus Task tab by default |
| Task + focus active + block active | D | [Focus Task] [Block Active] | Focus Task tab shows normal session UI + "Block running" chip at bottom |
| User taps "Block Active" tab | D | [Focus Task] [Block Active] | Block tab shows full standalone view (countdown, buttons, presets) |
| User taps "Block running · N apps · tap to manage" chip | D | [Focus Task] [Block Active] | Switches to Block tab |
| Task completes while on Block tab | B or A | None | `task` becomes null → `activeTab` resets to 'task' → State B (if block still active) or State A (if also expired) |
| Block expires while user is on Focus Task tab in State D | C | None | `standaloneActive` → false → State D collapses to State C, tab switcher gone |
| Block expires while user is on Block tab in State D | C | None | Same transition; user finds themselves on full task view |
| Multiple tasks active (otherActiveCount > 0) | C or D | — | "+N more active" chip shown inside task panel; tapping goes to Schedule |
| Accessibility permission missing | Any | — | Orange permission banner above content, always |
| Pomodoro enabled during focus | C or D | — | PomodoroStrip inside task panel, break button, break countdown |
| Focus PIN set, user taps Stop Focus | C or D | — | PinVerifyModal opens |
| No presets saved yet | A, B, or D | — | QuickPresetStrip shows "No saved presets yet" empty text |
| Presets exist | A, B, or D | — | QuickPresetStrip shows horizontal scrollable chips |

---

## The `handleAddTime` Function

FILE B's `handleAddTime` is already correct — use it as-is for ALL four time buttons (+30m, +1h, +2h, +4h):

```typescript
const handleAddTime = async (minutes: number) => {
  const baseMs = settings.standaloneBlockUntil
    ? Math.max(new Date(settings.standaloneBlockUntil).getTime(), Date.now())
    : Date.now();
  await setStandaloneBlockAndAllowance(
    settings.standaloneBlockPackages ?? [],
    baseMs + minutes * 60 * 1000,
    settings.dailyAllowanceEntries ?? [],
  );
};
```

In State B and in State D's block tab, the time buttons call:
- `handleAddTime(30)` → +30m
- `handleAddTime(60)` → +1h
- `handleAddTime(120)` → +2h
- `handleAddTime(240)` → +4h

Remove the `+8h (480min)` button that appeared only in split mode. Keep it consistent at 4 buttons everywhere.

---

## What NOT to Touch

- Every `useCallback`, `useEffect`, handler function: unchanged
- `usePomodoro`, `useTaskTimer`, `handlePomodoroBreakStart`: unchanged
- `handleActivateFocus`, `handleQuickBlock`, `handleSaveBlockPreset`, `handleDeleteBlockPreset`: unchanged
- `blockModal` JSX declaration: unchanged
- `StandaloneCountdown`, `TimerDisplay`, `PomodoroStrip`, `SecondaryBtn`, `QuickPresetStrip` components: unchanged
- `countdownStyles`, `timerStyles`, `pomStyles`: unchanged
- `withScreenErrorBoundary` export: unchanged
- `FOCUS_DEFENSE_HINT_DISMISSED_KEY`, `showDefenseHint`, `dismissDefenseHint`: unchanged

