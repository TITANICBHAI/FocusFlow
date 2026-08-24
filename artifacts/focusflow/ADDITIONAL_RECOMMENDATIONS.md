# FocusFlow — Additional Recommendations

These are optional product and reliability ideas collected during review. They are
kept separate from the required test plan. Status reflects the current checkout:

- `[x]` already implemented or substantially covered
- `[~]` partially implemented or needs verification
- `[ ]` suggestion not implemented

## 1 — Precise Budget-Expiry Trigger `[ ]`

The native ForegroundTaskService currently synchronizes allowance state on a
periodic schedule. Consider scheduling a precise callback for the exact remaining
time after each `usedMs` update, then re-checking the foreground package when it
fires. This would remove the possible polling delay without changing the
architecture.

## 2 — Friction Screen on Block Overlay `[x]`

`BlockOverlayActivity` and the WindowManager overlay already use a delayed
escape-control reveal with a countdown and block reason. Keep device-level tests
for the timer, overlay persistence, and dismissal gate.

## 3 — Timed Unlock / Cooldown `[ ]`

Consider a temporary allow action such as “Use for N minutes” that expires
automatically and then resumes blocking. This would extend the existing allowance
model rather than introduce another persistence layer.

## 4 — Unified Permission Health Checker `[ ]`

Permission state is still gathered in multiple UI paths. A shared health result
could expose consistent healthy/degraded/broken state and a single `canEnforce`
decision. Package-name matching is more resilient than matching a service class
name.

## 5 — NFC Focus Trigger `[ ]`

An NFC tag could toggle a focus session through a debounced
`focusflow://focus/toggle` URI. This is an optional, self-contained feature.

## 6 — Clean Up `daily_allowance_used` on Config Change `[ ]`

Remove usage entries for packages no longer present in the allowance configuration
when the native allowance sync runs, preventing unbounded SharedPreferences JSON
growth.

## 7 — Fix Default Settings Discrepancy `[~]`

Review the differing defaults for `autoCopyToAlwaysOn`,
`keepFocusActiveUntilTaskEnd`, and `vpnSelfHealEnabled` between `AppContext` and
the database. Choose one canonical source and add a regression test before
changing values.

## 8 — Guard Backup Restore During an Active Focus Session `[ ]`

Prevent a replacing backup import from deleting the task referenced by an active
focus session. Either stop the session first or reject the import with a clear
message.

## 9 — Validate SHA-256 Fallback Vectors `[x]`

The PIN suite now forces the pure-JavaScript fallback path and verifies the
empty-string and `abc` NIST vectors.

## 10 — Close the Standalone VPN Handoff Gap `[ ]`

When standalone VPN protection expires while always-on VPN packages remain,
ensure the always-on protection is established before the standalone tunnel is
stopped, or otherwise prove that no protection gap is observable.

## Priority Order

1. Canonicalize default settings and guard active-session backup restore.
2. Add the pure-JavaScript SHA-256 fallback vectors.
3. Clean allowance usage entries and close the VPN handoff gap.
4. Improve precise budget expiry and permission-state consistency.
5. Consider friction refinements, timed unlock, and NFC only after core enforcement
   and device evidence are stable.

The original unannotated source recommendation is preserved in the attached
workspace asset; this copy is the FocusFlow artifact's tracked status version.