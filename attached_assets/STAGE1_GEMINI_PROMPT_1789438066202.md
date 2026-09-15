# FocusFlow Migration — Stage 1 (Gemini)
## Pipeline position: Gemini (you, now) → Replit → Claude → GPT Terra → Claude (final review)

This stage is split into **9 prompts**. Paste them one at a time, in order, into the
same Gemini conversation — don't skip ahead and don't paste two at once. Wait for
each one to finish and check its report before moving to the next. Prompt 1 carries
the full context and rules; prompts 2 onward assume that context is already live in
the conversation and only give you the next batch of work.

---

## PROMPT 1 of 9 — Read this in full, then relocate files 1–9

### What this app is, and why fidelity matters here
FocusFlow is a published Android app that helps people break compulsive phone and app
use. It enforces scheduled focus sessions, blocks distracting apps, tracks a daily
allowance, runs a local VPN for network-level blocking, and locks settings behind a
PIN so the enforcement can't be casually switched off mid-session. This is a real,
currently-working app with real users depending on the blocking actually holding —
not a prototype and not a coding exercise.

You are stage 1 of a 5-stage migration relay from a React Native + Kotlin hybrid to
pure Kotlin. Every file you'll touch across these 9 prompts has already been verified
(by direct source inspection) to contain **zero React Native dependencies** — your
job across prompts 1–7 is relocation, not improvement. The next stage (Replit) will
read the files you produce and build repository classes that call directly into
them — if you drop a function, rename something, or "clean up" a piece of logic that
looks redundant, that mistake becomes invisible to Replit and gets built on top of,
three stages before anyone would normally notice.

### Before you do anything
Read `focusflowkotlin/ARCHITECTURE.md`, sections 2.2, 3.4, and 6, in full.

### Source (read-only — never modify)
```
artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/services/
artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/widget/
```

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/enforcement/receivers/   (marked below)
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/widget/                 (widget file only, later prompt)
```

### If you have shell or file-system access
Use `cp` followed by a find-and-replace on the package declaration and import lines.
Do **not** retype file contents from a chat response for anything over ~200 lines —
regenerating long files from memory is exactly how content silently goes missing. A
literal file copy cannot lose content; a retyped one can.

### The read-first gate — mandatory, per file, every prompt in this stage
For every file, before writing anything to the target path:
1. Open and read the entire source file, top to bottom.
2. Write one sentence stating what it does and one sentence naming its trickiest
   piece of logic (a state machine, a retry loop, a guard condition).
3. Only after that summary exists do you begin the copy.

If you can't write a specific, non-generic summary, you haven't read enough yet.
"Handles VPN stuff" means you skimmed. "Computes an effective VPN policy from
settings, debounces rapid changes, and discards stale async results using a
policy-generation counter" means you actually read it.

### Verification protocol — run this for every file, every prompt in this stage
1. **Line count**: source vs. output should match within a handful of lines
   (whitespace/import differences only).
2. **Structural count**: count occurrences of `fun `, `class `, `override fun`, and
   `const val` in the source file and in your output. These must match exactly. A
   matching line count with a mismatched function count means content was altered,
   not just reformatted — stop and re-copy.
3. Only mark a file done once both checks pass.

### Rules — apply for the rest of this stage
- Preserve every SharedPreferences key name exactly as found in source.
- Do not rename any function, class, or constant.
- Do not add annotations, dependency injection, or any pattern not already present.
- If something looks like a bug: **flag it in your report, do not fix it.**
- If you're uncertain whether something is safe to change: don't guess. Flag it and
  leave the source behavior intact.

### Files for this prompt

| # | File | Source lines | Target folder |
|---|---|---|---|
| 1 | BlockedAppDismissalPolicy.kt | 47 | enforcement/ |
| 2 | TemptationReportReceiver.kt | 50 | enforcement/receivers/ |
| 3 | WakeLockManager.kt | 62 | enforcement/ |
| 4 | NotificationActionReceiver.kt | 76 | enforcement/receivers/ |
| 5 | TaskEndAlarmReceiver.kt | 108 | enforcement/receivers/ |
| 6 | VpnRecoveryNotifier.kt | 111 | enforcement/ |
| 7 | FocusDayDeviceAdminReceiver.kt | 116 | enforcement/receivers/ |
| 8 | PackageInstallReceiver.kt | 136 | enforcement/receivers/ |
| 9 | BootReceiver.kt | 144 | enforcement/receivers/ — contains a dual-timestamp clock-tamper check. Do not simplify it. |

### Report for Prompt 1
```
| # | File | Source lines | Output lines | Struct. match (Y/N) | Flags |
|---|------|--------------|---------------|----------------------|-------|
```

---

## PROMPT 2 of 9 — Files 10–13

Same rules and verification protocol as Prompt 1 — this is just the next batch.

| # | File | Source lines | Target folder |
|---|---|---|---|
| 10 | TemptationLogManager.kt | 164 | enforcement/ |
| 11 | VpnWatchdogReceiver.kt | 198 | enforcement/receivers/ |
| 12 | AversiveActionsManager.kt | 218 | enforcement/ |
| 13 | TaskAlarmActivity.kt | 400 | enforcement/ |

### Report for Prompt 2
```
| # | File | Source lines | Output lines | Struct. match (Y/N) | Flags |
|---|------|--------------|---------------|----------------------|-------|
```

---

## PROMPT 3 of 9 — Files 14–17

Same rules and verification protocol as Prompt 1.

| # | File | Source lines | Target folder |
|---|---|---|---|
| 14 | VpnPolicyCoordinator.kt | 485 | enforcement/ — contains a "policy generation" version counter that discards stale async dispatch results so a fast settings change can't be overwritten by a slower one resolving late. Subtle, load-bearing. Copy it, don't rewrite it. |
| 15 | NetworkBlockerVpnService.kt | 563 | enforcement/ |
| 16 | BlockOverlayActivity.kt | 724 | enforcement/ |
| 17 | FocusFlowWidget.kt | 384 | widget/ — note different source folder (`android-native/.../widget/`, not `services/`) and different target folder |

### Report for Prompt 3
```
| # | File | Source lines | Output lines | Struct. match (Y/N) | Flags |
|---|------|--------------|---------------|----------------------|-------|
```

---

## PROMPT 4 of 9 — File 18: LauncherActivity.kt (1,926 lines)

Same rules and verification protocol as Prompt 1. This file is large enough to get
its own prompt. Copy it in complete, full. Once done, verify your output file is
approximately 1,926 lines — if it's meaningfully shorter, you lost content; re-copy
rather than proceeding. (This file has grown from an earlier bug-fix pass — icon
rounding, touch-gesture guards, status bar handling, and blur effects were added.
The higher count is expected, not a sign something is wrong with the source.)

Target: `enforcement/LauncherActivity.kt`

### Report for Prompt 4
```
| File | Source lines | Output lines | Struct. match (Y/N) | Flags |
|------|--------------|---------------|----------------------|-------|
```

---

## PROMPT 5 of 9 — File 19: ForegroundTaskService.kt (1,676 lines)

Same rules and verification protocol as Prompt 1. Same size-check discipline as
Prompt 4 — verify output is approximately 1,676 lines before considering this done.

Target: `enforcement/ForegroundTaskService.kt`

### Report for Prompt 5
```
| File | Source lines | Output lines | Struct. match (Y/N) | Flags |
|------|--------------|---------------|----------------------|-------|
```

---

## PROMPT 6 of 9 — File 20, Part A: AppBlockerAccessibilityService.kt, first half

This is the largest and highest-risk file in the entire codebase: 4,785 lines,
~40+ SharedPreferences key constants, and the app's entire anti-circumvention system
(detecting when a user is navigating toward the specific Android settings screens
that would disable this app's protections, and blocking that navigation mid-session).
It's split across this prompt and Prompt 7 because of its size.

Same rules and verification protocol as Prompt 1, applied with extra care here.

**Task**: copy the file from the top down through approximately line 2,400 — if your
tool supports reading by exact line number, use lines 1–2,400. If it doesn't, read to
the natural midpoint and stop at a clean function boundary near that line, not
mid-function. Write this partial content to `enforcement/AppBlockerAccessibilityService.kt`.
Do not close out the class or add anything that isn't in the source at that point —
Prompt 7 appends the rest to this same file.

If you're ever tempted to condense, summarize, or "clean up" anything in this file,
stop and leave it exactly as-is instead.

### Report for Prompt 6
State: which line you actually stopped at, how many lines you wrote, and confirm you
stopped at a function/class boundary rather than mid-statement.

---

## PROMPT 7 of 9 — File 20, Part B: AppBlockerAccessibilityService.kt, second half

Continue from exactly where Prompt 6 left off in the **same source file** —
`artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/services/AppBlockerAccessibilityService.kt`.
Copy from the line after where Prompt 6 stopped through the end of the file (line
4,785), and **append** it to the same target file you started in Prompt 6:
`enforcement/AppBlockerAccessibilityService.kt`. Do not create a second file.

Once appended, verify the combined target file is approximately 4,785 lines total —
add the line counts from Prompt 6 and this prompt and confirm they sum close to
4,785. If they don't, something was dropped at the seam between the two halves —
go back and check the boundary.

### Report for Prompt 7
```
Prompt 6 lines + Prompt 7 lines = total (should be ~4,785)
Structural count check (fun/class/const val) for the FULL assembled file vs. source: match Y/N
Flags:
```

---

## PROMPT 8 of 9 — Part 2: adapt 3 bridge modules (different kind of task)

Everything up to this point was verbatim relocation. This part is **not** — it's a
conversion. These three files wrap the highest-named-risk logic you just relocated,
and you're doing this instead of Replit (the next stage) because you now have direct,
fresh context on exactly what `VpnPolicyCoordinator`, `NetworkBlockerVpnService`, and
`TaskAlarmActivity` actually do — you just read them.

### Source (read-only)
```
artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/TaskAlarmModule.kt
artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/UsageStatsModule.kt
artifacts/focusflow/android-native/app/src/main/java/com/tbtechs/focusflow/modules/NetworkBlockModule.kt
```

### Target
```
focusflowkotlin/app/src/main/java/com/tbtechs/focusflow/data/repository/
```

### What changes vs. what stays the same
These files are `ReactContextBaseJavaModule` subclasses with `@ReactMethod`/`Promise`
bridge scaffolding. Strip that scaffolding; convert Promise-returning methods to
`suspend fun`. The logic underneath the scaffolding — the actual decisions being made
— must survive unchanged.

| Source | Target | Named risk — preserve exactly |
|---|---|---|
| `TaskAlarmModule.kt` | `data/repository/AlarmRepository.kt` | The fallback ladder `setAlarmClock` → `setExactAndAllowWhileIdle` → `setAndAllowWhileIdle`, tried in that order, gated by `canScheduleExactAlarms()`. Don't collapse this to one call. |
| `UsageStatsModule.kt` | `data/repository/UsageStatsRepository.kt` | The `AppOpsManager.checkOpNoThrow` permission guard in front of every usage-stats query. Without it, a revoked permission silently returns wrong allowance data instead of failing loudly. |
| `NetworkBlockModule.kt` | `data/repository/VpnRepository.kt` | Calls directly into `VpnPolicyCoordinator.kt`, which you relocated in Prompt 3 with its policy-generation counter intact. Re-read that file now — don't rely on memory of it from several prompts ago. Your repository must call into the real counter-aware logic, not reimplement a simpler version. |

### Rules for this prompt
- Every public method on the old `@ReactMethod` bridge function needs a matching
  method on your new class — list them side by side (old signature → new signature).
- If you're not sure a signature is right: say so explicitly, don't invent one.
- Flag bugs, don't fix them.

### Report for Prompt 8
```
| Source file | Target file | Public methods (old → new) | Named risk preserved? (Y/N + how you verified) | Flags |
|---|---|---|---|---|
```

---

## PROMPT 9 of 9 — One consolidated final report

Pull together everything from Prompts 1–8 into two single tables, so this stage has
one clean handoff document instead of nine scattered ones.

**Table 1 — all 20 relocated files (Prompts 1–7):**
```
| # | File | Source lines | Output lines | Struct. match (Y/N) | Flags |
|---|------|--------------|---------------|----------------------|-------|
```

**Table 2 — all 3 adapted repositories (Prompt 8):**
```
| Source file | Target file | Public methods (old → new) | Named risk preserved? (Y/N) | Flags |
|---|---|---|---|---|
```

List every flag raised across all 9 prompts underneath, even ones already mentioned —
this is the version that gets handed to the next stage, and it should be complete on
its own without anyone needing to scroll back through the earlier prompts.
