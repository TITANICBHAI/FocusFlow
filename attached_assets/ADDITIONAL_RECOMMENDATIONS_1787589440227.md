# FocusFlow — Additional Recommendations
## Things worth doing that didn't fit neatly into the fix plan

---

## 1 — Precise Budget-Expiry Trigger (Replace 60-Second Allowance Poll)

**Current behavior:**
ForegroundTaskService syncs allowance state every 60 seconds. When A11y is dead and a
time_budget is exhausted, blocking can be delayed up to 60 seconds. A user with 1 minute
of Instagram budget gets an extra 1 minute free on every OEM kill cycle.

**The fix (from Curbox's `setUpForcedRefreshChecker` pattern):**
When FTS writes an updated `usedMs` during its sync, calculate exactly how many
milliseconds remain in the budget. Schedule a `Handler.postDelayed` callback at that
exact moment. When the callback fires, re-check the foreground app and block if still
there.

This eliminates the 60-second lag entirely. The block fires at the exact millisecond
the budget runs out, not at the next poll.

**Effort:** Medium. Contained change inside `syncAllowanceFromUsageStats`. No
architecture changes.

---

## 2 — Friction Screen on Block Overlay (From Curbox's `WarningActivity`)

**Current behavior:**
When a blocked app is detected, the user is immediately sent to the Home screen. No
friction, no delay. A determined user simply reopens the app.

**The improvement:**
Show `BlockOverlayActivity` with a mandatory countdown (10–30 seconds) before dismissal.
During the countdown, the overlay displays why the app is blocked and how much budget
remains. The user must wait the full countdown. This breaks the impulse-open loop that
makes app blocking ineffective — people stop trying to open Instagram every 2 minutes
when each attempt costs 30 seconds of waiting.

Curbox's version also supports a math challenge as an alternative to the countdown.
That level of complexity is optional, but even a plain countdown meaningfully improves
effectiveness.

**Effort:** Low. `BlockOverlayActivity` already exists. Add a countdown timer and
delay the dismiss button.

---

## 3 — Timed Unlock / Cooldown (From Curbox's AppBlocker)

**Current behavior:**
Apps are either blocked or not. There is no way to say "allow Instagram for 15 minutes
and then block it again."

**The improvement:**
Add a "Use for N minutes" option to the block overlay screen. When granted, the app gets
a temporary allow that expires at a set time. After it expires, blocking resumes
automatically.

This maps directly onto FocusFlow's existing daily allowance system — add a short-term
`temporaryAllowUntil` field to the allowance entry, clear it when expired, check it
before enforcing the block. No new persistence layer needed.

This is more user-friendly than the current all-or-nothing model. Users are more likely
to set strong blocks when they know they have a legitimate escape route.

**Effort:** Medium. Touches the allowance entry type, A11y enforcement check, and the
block overlay UI.

---

## 4 — Unified Permission Health Checker (From Flint's `PermissionHealthChecker`)

**Current behavior:**
Permission checks are scattered. The Focus tab polls `hasAccessibilityPermission()`. The
Permissions screen runs its own set of checks. The orange header banner has its own
polling. All three can show different states if one hasn't refreshed yet.

**The improvement:**
One `PermissionHealthChecker` data class that reads all permissions once and exposes a
single `canEnforce` boolean plus a `HealthLevel` enum (HEALTHY / DEGRADED / BROKEN).
All UI reads from this one source. One refresh, consistent state everywhere.

Also: Flint checks accessibility by package name, not class name:
```kotlin
enabled.split(':').any { entry ->
    ComponentName.unflattenFromString(entry)?.packageName == context.packageName
}
```
Class-name matching breaks if you ever rename the service class. Package-name matching
is more robust.

**Effort:** Low. Consolidation refactor, no behavior change.

---

## 5 — NFC Focus Trigger (From Curbox's `NfcFocusHandler`)

**What it does:**
Tap an NFC tag stuck to your desk → FocusFlow starts a focus session. Tap again → stop.
Physical triggers are more effective than app-based ones because they involve a
deliberate physical action.

**Implementation:**
- Handle `ACTION_NDEF_DISCOVERED` in root `_layout.tsx` or a native Activity
- URI scheme: `focusflow://focus/toggle?taskId=X&mins=Y`
- 4-second debounce after writing a tag (prevents accidental re-scan)
- The full logic in Curbox's `NfcFocusHandler` is ~150 lines

**Effort:** Medium. New feature, clean self-contained addition. Does not touch existing
enforcement.

---

## 6 — Clean Up `daily_allowance_used` on Config Change

**Current behavior:**
`daily_allowance_used` in SharedPrefs stores one entry per package that has ever been in
an allowance config. When a package is removed from the allowance config, its usage
entry stays forever. On a device used over months with many config changes, this JSON
grows large.

**The fix:**
When `syncAllowanceFromUsageStats` runs (every 60 seconds in FTS), remove any entry in
`daily_allowance_used` whose package is not in `daily_allowance_config`. This is a
simple keyset intersection. One-line addition.

**Effort:** Trivial.

---

## 7 — Fix Default Settings Discrepancy

**The problem:**
`AppContext.defaultSettings` and `database.ts DEFAULT_SETTINGS` differ on three fields:
- `autoCopyToAlwaysOn`: AppContext = `true`, database = `false`
- `keepFocusActiveUntilTaskEnd`: AppContext = `true`, database = `false`
- `vpnSelfHealEnabled`: AppContext = `true`, database = undefined

First-time users (no DB yet) get AppContext defaults. Returning users who reinstall
get database defaults. Behavior differs between new installs and updates.

**The fix:**
Pick one canonical source of truth for defaults and make both files reference it. The
safest path is to define a single `DEFAULT_APP_SETTINGS` constant in `types.ts` and
import it into both `AppContext.tsx` and `database.ts`.

**Effort:** Trivial. Pure refactor, no behavior change (but you must decide which
values are correct first).

---

## 8 — Guard Backup Restore When Focus Session Is Active

**The problem:**
If a `replaceTasks=true` backup import runs while a focus session is active, all tasks
are deleted from the database. The focus session still has a `taskId` pointing to a
now-deleted task. The focus ring shows an empty state and the session cannot be properly
terminated because the linked task no longer exists.

**The fix:**
Before running a `replaceTasks=true` import, check if `state.focusSession !== null`.
If a session is active:
- Option A: block the import with a message ("Stop your focus session first").
- Option B: call `stopFocusMode()` automatically before proceeding, then import.

Either is acceptable. The current behavior (silently orphaning the session) is not.

**Effort:** Trivial.

---

## 9 — Validate SHA-256 Fallback Against Known Test Vectors

**The problem:**
`pinCrypto.ts` has a pure-JS SHA-256 implementation used when `globalThis.crypto.subtle`
is unavailable. This fallback is never validated. A silent implementation error would
produce wrong hashes that appear valid — users would be unable to unlock anything after
setting a PIN.

**The fix:**
Add one unit test file: `pinCrypto.test.ts`. Test both paths (Web Crypto and pure-JS
fallback) against two NIST SHA-256 known-answer vectors:
- `SHA256("") = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"`
- `SHA256("abc") = "ba7816bf8f01cfea414140de5dae2ec73b00361bbef0469f492a9f3d95d7f4b4"`

**Effort:** Trivial.

---

## 10 — Address VPN 400ms Gap on Standalone Block Expiry

**The problem:**
When a standalone block with VPN expires:
1. `stopNetworkBlock()` — VPN stops.
2. 400ms wait.
3. `startNetworkBlock(alwaysOnVpnPackages)` — VPN restarts for always-on.

During those 400ms, the always-on VPN is completely inactive. On some devices, apps
that monitor connectivity state can detect this gap. A network-aware blocked app could
theoretically make connections during that window.

**The fix:**
Reverse the order — start the always-on VPN first, THEN stop the standalone VPN. Since
both connect to the same local TUN, overlapping them briefly is harmless. The always-on
protection never fully drops.

```typescript
// Current (wrong order)
await NetworkBlockModule.stopNetworkBlock();
await delay(400);
await NetworkBlockModule.startNetworkBlock(alwaysOnVpnPackages, 'per_app');

// Fixed (correct order)
if (alwaysOnVpnPackages.length > 0) {
  await NetworkBlockModule.startNetworkBlock(alwaysOnVpnPackages, 'per_app');
}
await NetworkBlockModule.stopNetworkBlock();
```

**Effort:** Trivial. One-line reorder.

---

## Priority Order

| Priority | Item | Why |
|----------|------|-----|
| 1 | Fix default settings discrepancy | Silent behavior difference between new/returning users |
| 2 | Guard backup restore with active session | Orphaned session is unrecoverable without force-stop |
| 3 | SHA-256 fallback test vectors | One-time addition; PIN failure is catastrophic |
| 4 | Clean up `daily_allowance_used` | Technical debt before allowance system grows more |
| 5 | VPN 400ms gap fix | Trivial reorder, closes a real window |
| 6 | Precise budget-expiry trigger | Meaningful enforcement accuracy improvement |
| 7 | Unified PermissionHealthChecker | Code quality; prevents UI state inconsistencies |
| 8 | Friction screen on block overlay | High product impact, low effort |
| 9 | Timed unlock / cooldown | Strong product feature, medium effort |
| 10 | NFC focus trigger | Novel feature, medium effort |

