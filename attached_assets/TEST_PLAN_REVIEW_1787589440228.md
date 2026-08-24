# FocusFlow Test Plan — Review & Gap Analysis

---

## What's Already Good — Keep As-Is

**Section 1.4 (Deterministic time):** The discipline of injecting a fixed clock into all
tests is the single most important rule in the document. Do not loosen it.

**Section 1.5 (Test exemptions as carefully as blocking):** Critical for an enforcement
app. A test that only verifies blocking works is half a test. The other half is proving
the dialer, system UI, and FocusFlow itself are never caught.

**Section 2.6 (Cross-layer contract tests):** Correctly identifies that a passing JS mock
is not evidence that Kotlin enforcement works. The distinction matters here more than in
most apps because enforcement is split across the bridge.

**Section 9.4 (Fallback enforcement):** Exists, which is the right instinct. Most test
plans for mobile apps omit fallback paths entirely.

**Section 10.2 (Overnight windows):** Has the right test cases listed.

**Section 24.4 (Process death and restart):** Comprehensive.

---

## Gap 1 — Section 9.4 Would Pass Even With the Early-Exit Bug Still Present

**The problem:** The plan tests "fallback blocks an exhausted allowance" and
"always-on-only configuration activates fallback polling." Those tests check the
*outcome* but not the *precondition* that makes the outcome possible.

The actual bug was that `if (!focusActive && !saActive && !hasGreyout) return` shut
the poll down entirely before any allowance or always-on check ran. A test that mocks
a blocked app and checks whether blocking occurred would only verify the check *when
the poll runs*. It does not verify that the poll *stays running* when only allowance
or always-on is configured.

**Add explicitly:**
- Test that the fallback poller does NOT exit early when focus is off, standalone is off,
  greyout is empty, but always-on is active.
- Same test for allowance-only configuration.
- Specifically: assert that the polling callback is still scheduled for the next interval
  after the check, not terminated.

---

## Gap 2 — Section 16.3 Tests Launch Count, Not Session Duration

**The problem:** The plan says "repeated same-package resumed events do not reset the
session start." That asserts the start is preserved. But the consequence of the bug was
not just a moved start — it was *shorter recorded duration*.

A test that checks the session start field is correct but doesn't prove the *duration*
written to the output is correct. The bug could be partially fixed (start preserved
under some conditions) while duration is still wrong.

**Add a concrete scenario:**
- App opens at T=0 minutes.
- Samsung fires a second RESUMED at T=5 minutes (intra-app navigation, same package).
- App closes at T=30 minutes.
- Assert: `foregroundMs` recorded = 30 minutes, not 25 minutes.
- Also assert: `launchCounts` = 1, not 2 (the duplicate RESUMED should not inflate count).

---

## Gap 3 — Section 9.2 Screen-Off Tests Don't Cover the FTS Cross-Service Timing

**The problem:** Section 9.2 says "screen-off time is not charged." This tests the A11y
service pausing correctly. It does not test what happens when A11y pauses the timer and
FTS fires its 60-second sync at the same time.

FTS reads `queryUsageStats + totalTimeInForeground`. On some API levels, Android's
`totalTimeInForeground` includes screen-off time. FTS writes are raise-only, so if FTS
writes a higher value than A11y accumulated, the budget gets charged screen-off time
through the back door even though A11y paused correctly.

**Add:**
- Test where A11y has paused a session at T=10min and FTS sync fires with
  `totalTimeInForeground = 18min` (includes screen-off).
- Assert SharedPrefs `usedMs` = 18min (FTS raised it).
- Screen unlocks. A11y resumes with 12 minutes remaining (budget 30min, used 18min).
- Assert A11y does NOT add T=10min again. No double-charge on resume.

---

## Gap 4 — Section 18.2 Overrun Test Would Pass Despite the Race Condition

**The problem:** The plan says "repeated delivery of the same overrun event does not
extend the task twice if the persisted state makes it identifiable as already handled."

The phrase "if the persisted state makes it identifiable" assumes the state correctly
marks the task as handled. Based on code analysis, the only guard is checking if the task
is `completed` or `skipped`. A task extended by the user is still `scheduled`, so the
background OVERRUN_CHECK handler will extend it again if the old end-notification fires
before the new notifications are scheduled.

A test that fires OVERRUN_CHECK twice with the second seeing a `completed` task passes
trivially. It does not reproduce the race.

**Add the actual race scenario:**
- Task T scheduled 10:00–11:00.
- User extends by 30 minutes via UI at T=11:00:00 (new end = 11:30).
- Old `${taskId}-end` notification fires at T=11:00:01 (before cancel completes).
- Background handler runs, finds task still `scheduled`, extends again by 10 minutes.
- Assert: task end = 11:30, not 11:40. Assert `extendTaskTime` was called exactly once,
  not twice.

---

## Gap 5 — Section 4.3 Tests the Wrong Expected Behavior for MEDIUM Priority

**The problem:** The plan says "medium/low tasks follow the configured maximum
auto-shift threshold." MEDIUM tasks are currently auto-skipped, same as LOW. The code
comment says MEDIUM should go to `needsUserConfirm` (user is alerted) but the
implementation silently drops them.

A test asserting that MEDIUM tasks are auto-skipped will pass — but it is asserting
incorrect behavior. This test will silently bless a known bug.

**Two acceptable paths:**
1. Fix the code so MEDIUM goes to `needsUserConfirm`, then test that.
2. Mark the test explicitly as "verifying known-incorrect behavior — Bug #3 tracker"
   so it is not mistaken for correct behavior in code review.

Either is fine. What is not acceptable is a green test that nobody knows is testing
a bug.

---

## Gap 6 — No SharedPreferences JSON Shape Contract Tests

**The problem:** FTS reads `daily_allowance_used` written by A11y as a JSON object with
a specific structure. If A11y ever changes how it writes that structure, FTS reads
wrong data and enforcement silently breaks — no crash, no error, wrong behavior.

The same risk applies to: `daily_allowance_config`, `always_block_packages`,
`greyout_schedule`, `allowed_packages`.

These are the most critical inter-service contracts in the entire app and they are
currently unverified.

**Add a contract fixture test for each SharedPrefs key:**
- Define the exact JSON structure as a constant fixture.
- Write an A11y-side test: A11y produces this exact JSON when given a known input.
- Write an FTS-side test: FTS correctly deserializes this exact JSON and produces
  the expected enforcement decision.
- Any divergence in serialization format immediately becomes a test failure.

This is the test equivalent of an API contract — the two services agree on the shape,
and any change to either side breaks it visibly.

---

## Gap 7 — Section 10.2 Tests Time Wrap But Not the Weekday Carry-Over

**The problem:** Section 10.2 correctly tests that a 22:00–06:00 window blocks at 05:00
the next calendar day. That tests the time arithmetic. It does not test the weekday
dimension.

Specific failing scenario: schedule configured for **Monday only** (days = [2]),
times 22:00–06:00. At Tuesday 02:00, the block should be active (Tuesday morning is
inside Monday night). But if the check evaluates Tuesday's weekday against a
Monday-only schedule, the block fails.

**Add:**
- Schedule: `days=[Monday], start=22:00, end=06:00`.
- Test at Tuesday 02:00 → assert: blocked.
- Test at Tuesday 06:01 → assert: not blocked.
- Test at Monday 21:59 → assert: not blocked.
- Test at Tuesday 07:00 → assert: not blocked.

This is a distinct test from the time-wrap test. Both are required.

---

## Gap 8 — `getFallbackForegroundPackage` API Not Explicitly Verified

**The problem:** Section 9.4 mentions foreground detection uses "the latest foreground
event" but does not explicitly test that `queryEvents` is used rather than
`queryUsageStats`.

A regression to the buggy `queryUsageStats` implementation would be invisible to the
current test: both APIs can return the correct app in happy-path conditions. The
difference only appears when a different app was used earlier in the day.

**Add the distinguishing scenario:**
- Inject a `queryEvents` stream: Instagram `ACTIVITY_RESUMED` at T-3 seconds.
- Inject a `queryUsageStats` result: Gmail `lastTimeUsed` at T-1 second (Gmail was
  opened earlier today).
- Assert `getFallbackForegroundPackage` returns Instagram, not Gmail.
- This scenario can only pass if `queryEvents` is used, not `queryUsageStats`.

---

## Gap 9 — FTS Raise-Only Guarantee Is Untested

**The problem:** FTS's allowance sync is designed to only write `usedMs` if the new
value from UsageStats is higher than what is currently in SharedPrefs. A bug where FTS
writes a lower value would silently reset a user's Instagram budget from 28 minutes used
back to 0, giving them unlimited access.

No test verifies this property.

**Add:**
- SharedPrefs has `usedMs = 20 minutes` for Instagram.
- FTS sync reads `totalTimeInForeground = 15 minutes` (possible if OEM resets stats,
  or DST clock adjustment, or UsageStats bug).
- Assert SharedPrefs still shows `usedMs = 20 minutes`, not 15 minutes.
- The raise-only property is preserved.

---

## Gap 10 — Budget Exhausted During Screen-Off Not Tested

**The problem:** Section 9.2 says "if no time remains, the user is sent to Home screen."
This tests the obvious path. It does not test the specific path where the budget is
exhausted not by the user actively using the app, but by FTS writing a higher value
while A11y's timer is paused.

**Add:**
- User has 30-minute budget. Uses app for 28 minutes. Screen turns off.
- A11y pauses session correctly (0 minutes charged during screen-off).
- FTS sync fires and writes `usedMs = 31 minutes` (from `totalTimeInForeground` which
  includes some pre-pause accumulation).
- User unlocks phone.
- Assert: user is immediately sent to Home screen (no more budget).
- Assert: no additional time is charged on unlock — the resume handler detects
  exhausted budget before restarting timers.

---

## Additional Tests Recommended (Not in Current Plan)

### A — 500-alarm limit on bulk import

Not mentioned anywhere in the current plan. The screenshot confirms this bug exists.

Test: import a backup containing 50 future-scheduled tasks. Assert that the total count
of scheduled notifications and native AlarmManager registrations stays below 450 (the
configured JS-side budget). With the fix applied, only tasks starting within the next
48 hours should be scheduled. Verify tasks beyond 48 hours have no notifications until
their day arrives.

### B — `replaceTasks=true` while focus session is active

Not tested anywhere. If a full replacement import runs while a focus session is active:
- All tasks are deleted from DB.
- The focus session still has a `taskId` pointing to the deleted task.
- Assert: either the import is blocked with an explanation, or the focus session is
  cleanly terminated before the delete runs.
- Assert: ForegroundTaskService is not left in ACTIVE mode pointing to a ghost task.

### C — SHA-256 fallback produces correct hashes

Not tested anywhere. The pure-JS SHA-256 implementation in `pinCrypto.ts` is used when
Web Crypto API is unavailable. A silent implementation error would produce wrong hashes
that appear valid — users would be unable to unlock anything after setting a PIN.

Test against NIST SHA-256 known-answer test vectors. At minimum:
- SHA-256("") = `e3b0c44298fc1c149afb...` (known empty-string hash)
- SHA-256("abc") = `ba7816bf8f01cfea4141...` (known test vector)

Run these tests for both the Web Crypto path and the pure-JS fallback path.

### D — Always-on + allowance for the same package

What happens when a package is in both the always-on block list AND the daily allowance
config? Based on the code comment: "Daily allowance does NOT override an explicit block.
The block list check runs first."

Test: Instagram in always-on block list AND in daily allowance with 5 opens/day.
Count opens used = 0. Assert: Instagram is blocked even on first open (always-on wins).
The allowance counter should NOT increment (the app was never allowed through).

### E — `autoCopyToAlwaysOn` dual-origin removal bug

When a package is manually added to always-on AND auto-copied from a standalone block,
the standalone block expiry removes it from always-on even though the user added it there
independently.

Test:
- Manually add YouTube to always-on.
- Set a standalone block that includes YouTube with `autoCopyToAlwaysOn = true`.
- The auto-copy sees YouTube already in always-on — should record it in
  `autoCopiedAlwaysOnPackages` or NOT, depending on correct behavior.
- Standalone block expires.
- Assert: YouTube REMAINS in always-on (because it was manually added).
- Current code will remove it — this test is expected to FAIL, tracking the bug.

### F — VPN 400ms gap on standalone block expiry

When a standalone block expires, VPN is stopped then restarted for always-on packages
after a 400ms delay. During that gap, always-on VPN is completely inactive.

Test:
- Set standalone block with VPN enabled. Set always-on VPN packages separately.
- Advance clock to standalone block expiry.
- Monitor VPN service state continuously at 50ms intervals.
- Assert: VPN is never fully stopped AND restarted with a gap. The always-on VPN
  restart should happen before or simultaneously with stopping the standalone VPN, not
  400ms after.

---

## Priority Order for Gaps

| Priority | Gap | Reason |
|----------|-----|--------|
| 1 | Gap 8 — API distinguishing test for `getFallbackForegroundPackage` | Foundation for all fallback tests |
| 2 | Gap 6 — SharedPrefs JSON shape contract tests | Silent failures, hardest to debug |
| 3 | Gap 1 — Fallback early-exit specifically tested | The exact bug that caused full allowance failure |
| 4 | Gap 4 — Overrun double-extension race | Real race condition, hard to reproduce otherwise |
| 5 | Gap 2 — Session duration not just start time | Quantitative correctness, Samsung users affected |
| 6 | Gap 7 — Overnight weekday carry-over | Distinct from time-wrap, both required |
| 7 | Gap 5 — MEDIUM tasks asserted as auto-skip | Test should not bless a known bug |
| 8 | Gap 3 — FTS sync during screen-off pause | Cross-service timing, API-level dependent |
| 9 | Gap 9 — FTS raise-only guarantee | Edge case but silent budget reset is serious |
| 10 | Gap 10 — Budget exhausted during screen-off | Specific path through the resume handler |
| 11 | Add A — 500-alarm limit | Confirmed by screenshot, easy to test |
| 12 | Add B — Backup import with active session | Orphan session scenario |
| 13 | Add C — SHA-256 fallback vectors | One-time addition, never needs updating |
| 14 | Add D — Always-on + allowance same package | Interaction between two systems |
| 15 | Add E — autoCopyToAlwaysOn dual-origin | Known bug, test should track it |
| 16 | Add F — VPN 400ms gap | Architectural timing issue |

