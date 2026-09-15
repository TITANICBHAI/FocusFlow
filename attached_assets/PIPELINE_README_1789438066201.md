# FocusFlow Migration Pipeline — Start Here

Five stages, in order. Don't run them out of order — each one depends on the real
output of the one before it, not a description of what that output should look like.

## Files in this folder

| File | What it is |
|---|---|
| `ARCHITECTURE.md` | The full migration blueprint — 7 sections, built from direct source reading. Everyone in the pipeline reads relevant sections of this before touching anything. |
| `STAGE1_GEMINI_PROMPT.md` | Relocates 20 native enforcement files verbatim, then adapts 3 specific bridge modules — split into **9 sequential prompts**, paste one at a time into the same Gemini conversation. |
| `STAGE2_REPLIT_PROMPT.md` | Converts the remaining bridge modules and supporting logic into repositories — split into **7 sequential prompts**. |
| `STAGE3_CLAUDE_PROMPT.md` | My own work — Room database, PIN/Keystore system, ViewModels, and the analytics engine. Split into **4 tracks**: A (Room) and B (PIN) run in parallel in two separate conversations, C (ViewModels) runs after both finish, D (analytics engine — 4 sub-prompts) can run any time after the zip is available since it doesn't depend on A/B/C's output, only on real source files. |
| `STAGE4_GPT_TERRA_PROMPT.md` | Rebuilds all screens and components in Jetpack Compose — split into **12 sequential prompts**, one screen-family at a time. |
| This file | The order to run them in, and what to hand each one. |

Stage 5 doesn't have a prompt file — it's mine, and I already have the full context.
Just bring me everything from all four stages when it's time.

## The order

| Stage | Who | Model | Effort setting | You give them | They hand back |
|---|---|---|---|---|---|
| 1 | Gemini | Gemini 3.8 Flash | Not adjustable on your end — the prompt compensates with structural line/function-count verification instead of relying on a setting. | The zip, `ARCHITECTURE.md`, `STAGE1_GEMINI_PROMPT.md` | `enforcement/` (20 files) + 3 adapted repositories (`AlarmRepository`, `UsageStatsRepository`, `VpnRepository`) + two completed report tables |
| 2 | Replit Agent (runs GPT-5.6 Luna) | GPT-5.6 Luna 5.6 | Not adjustable on your end — the prompt compensates with a "re-read before using, don't trust session memory" rule instead. | The zip, `ARCHITECTURE.md`, `STAGE2_REPLIT_PROMPT.md`, **Gemini's real `enforcement/` output** | `data/repository/`, `background/`, `notifications/`, `domain/` + a completed report table |
| 3A + 3B | Me — two separate conversations, run at the same time | Sonnet 5 | **High** in both | Track A (Room) needs nothing extra; Track B (PIN) needs nothing extra — both are self-contained, see `STAGE3_CLAUDE_PROMPT.md` | Track A: Room database + 2 repositories. Track B: `PinManager`, `PinReuseTracker`, `PinSessionState` |
| 3C | Me — after 3A and 3B both finish | Sonnet 5 | **Max** | Real output from 3A, 3B, Replit, and Gemini | 4 ViewModels wired to real repositories — **see the open item below, a 5th is needed for stats** |
| 3D | Me — 4 sub-prompts, can run any time after the zip is available, doesn't need 3A/3B/3C | Sonnet 5 | **High** | The zip only — reads real TS source directly, no dependency on the other tracks | Completed + ported `AchievementEngine.kt`, ported rule files + templates, ported `AnalyticsProcessor.kt` + `InsightEngine.kt` + **`StatsViewModel.kt`**, new `AppSettings` fields, `NotificationRepository.kt` |
| 4 | GPT Terra | GPT-5.6 Terra | **High**, bump to **xhigh** where flagged inside the prompt | The zip, `ARCHITECTURE.md`, `STAGE4_GPT_TERRA_PROMPT.md`, everything from stages 1–3 (especially my real ViewModel files **and Track D's analytics output**) | `ui/` — all screens and components + a completed report table |
| 5 | Me | Sonnet 5 | **Max** | Everything from all four stages | Final integration review — see checklist below |

## Why this order and not another
Each stage calls into the previous one's real files, not a promise that they'll
exist. Gemini goes first because its relocation work depends on nothing. Replit goes
second because its repositories call directly into what Gemini just produced. I go
third because my ViewModels need Replit's actual method signatures, not a guess. GPT
Terra goes last of the build stages because its screens need my actual ViewModels to
compile against. I review last because that's the only point where all four stages'
work exists at once and can be checked against each other, not just individually.

## Before forwarding any stage's output to the next one
Open its report table and check:
- Stage 1: do all 20 rows show a structural match? Any flags? (Gemini's stage is 9
  prompts run in one conversation — check each prompt's own mini-report before
  pasting the next one in, not just the final consolidated report at the end.)
- Stage 2: does every bridge module's public method list have a plausible Kotlin
  counterpart? Any flags?
- Stage 4: does every screen's interactive-element count match between "found" and
  "matched"? Any flags?

If something's flagged or mismatched, send it back to that same stage before moving
on — a mistake is far cheaper to fix at the stage that made it than three stages
later, when someone else has already built on top of it.

## What I'll specifically re-check at Stage 5
- The VPN policy-generation counter in `VpnPolicyCoordinator` survived Gemini's move
  intact (§6, Risk 3 in `ARCHITECTURE.md`).
- `AppBlockerAccessibilityService.kt` is still ~4,785 lines and not quietly condensed.
- The exact-alarm fallback ladder in the new `AlarmRepository` still tries all three
  methods in order, not just the first one.
- The allowance-tracking duplication (`ForegroundTaskService` vs.
  `AppBlockerAccessibilityService`, §6 Risk 1) still uses matching key names on both
  sides.
- Every screen GPT Terra built actually calls a method that exists on my real
  ViewModels — not a plausible-looking one that doesn't compile.
- `AnalyticsProcessor.kt` actually calls `GreyoutRepository.getTemptationLog()` and
  not a stub — this only exists if Replit's Stage 2 Prompt 3 was done correctly.
- Every achievement condition in the ported `AchievementEngine.kt` references a
  `LifetimeStats` field that's actually in Track A's Room-backed implementation —
  Track D added fields that Track A didn't originally plan for (`lastSessionAt`),
  and the two tracks can run independently, so this is a real seam-check, not a
  formality.
- Rule counts in the ported Kotlin files match source exactly: 7 (Yesterday), 11
  (Weekly), 8 (Three Month) — a silent drop here means the engine looks like it
  works but occasionally has nothing to say when it should.

## Resolved — StatsViewModel
Built in Track D Prompt 3, alongside `AnalyticsProcessor.kt` and `InsightEngine.kt`,
not in Track C. This keeps Track D independent of Track C's completion — it can
still run any time after the zip is ready, with no new cross-track dependency.
