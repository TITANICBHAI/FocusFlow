# FocusFlow — Additional Recommendations

These are optional product and reliability ideas collected during review. They are
kept separate from the required test plan. Status reflects the current checkout:

- `[x]` already implemented or substantially covered
- `[~]` partially implemented or needs verification
- `[ ]` suggestion not implemented
- `[N/A]` intentionally outside the current FocusFlow product scope

This status was checked against the current TypeScript and Kotlin implementation
on 2026-08-24. A recommendation is not marked complete merely because it is
described in a comment, plan, or test stub.

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

## 3 — Timed Unlock / Cooldown `[N/A]`

The current product does not expose an in-session “Use for N minutes” bypass.
FocusFlow instead supports configured daily allowances and temporary blocking
sessions, which are different controls. Adding a timed unlock would be a product
decision and is intentionally outside the current enforcement scope.

## 4 — Unified Permission Health Checker `[ ]`

Permission state is still gathered in multiple UI paths. A shared health result
could expose consistent healthy/degraded/broken state and a single `canEnforce`
decision. Package-name matching is more resilient than matching a service class
name.

## 5 — NFC Focus Trigger `[N/A]`

There is no NFC-trigger product requirement or NFC integration in the current
FocusFlow scope. The existing `focusflow://` deep-link handling is used for app
navigation, not NFC-based session toggling. This remains optional and is not
needed for enforcement correctness.

## 6 — Clean Up `daily_allowance_used` on Config Change `[ ]`

Remove usage entries for packages no longer present in the allowance configuration
when the native allowance sync runs, preventing unbounded SharedPreferences JSON
growth.

## 7 — Fix Default Settings Discrepancy `[~]`

Review the differing defaults for `autoCopyToAlwaysOn`,
`keepFocusActiveUntilTaskEnd`, and `vpnSelfHealEnabled` between `AppContext` and
the database. Choose one canonical source and add a regression test before
changing values. The discrepancy is confirmed in live code: the React defaults
are `true` for all three, while the database defaults currently use `false` for
`autoCopyToAlwaysOn` and `keepFocusActiveUntilTaskEnd` and do not define the same
`vpnSelfHealEnabled` default in that defaults object. No value was changed during
this status review.

## 8 — Guard Backup Restore During an Active Focus Session `[ ]`

Prevent a replacing backup import from deleting the task referenced by an active
focus session. Either stop the session first or reject the import with a clear
message. Live code still allows `replaceTasks` to call `deleteTask` for every
existing task; there is no active-session check in the restore path.

## 9 — Validate SHA-256 Fallback Vectors `[x]`

The PIN suite now forces the pure-JavaScript fallback path and verifies the
empty-string and `abc` NIST vectors.

## 10 — Close the Standalone VPN Handoff Gap `[ ]`

When standalone VPN protection expires while always-on VPN packages remain,
ensure the always-on protection is established before the standalone tunnel is
stopped, or otherwise prove that no protection gap is observable. Live code
merges always-on and standalone VPN package lists when starting protection, but
the standalone-expiry path still needs Android-level evidence that the handoff
does not expose a gap.

## Priority Order

1. Canonicalize default settings and guard active-session backup restore.
2. Clean allowance usage entries and close the VPN handoff gap.
3. Improve precise budget expiry and permission-state consistency.
4. Keep device-level friction-overlay verification in the Android test backlog.
5. Timed unlock and NFC are intentionally out of scope unless product requirements
   change; the SHA-256 fallback vectors are already complete.

The original unannotated source recommendation is preserved in the attached
workspace asset; this copy is the FocusFlow artifact's tracked status version.