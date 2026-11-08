# FocusFlow persistence contract

## Room owns relational history

Room is the source of truth for tasks, focus sessions, focus overrides, daily
completions, achievements, weekly insights, and report notes. Report notes use
the legacy-compatible `report_notes(ref_date, type, note, updated_at)` shape
with `(ref_date, type)` as the primary key. Both `day` and `week` are valid
types.

## SharedPreferences owns native enforcement state

The `focusday_prefs` namespace remains the compatibility surface for state that
native services must read immediately: active focus snapshots, standalone
blocking, allowance state, VPN state, launcher state, and setup recovery flags.
Ordinary UI preferences remain candidates for a later DataStore migration.

## Time and JSON rules

- Persisted timestamps are UTC ISO-8601 strings.
- Date-only values, including `daily_completions.date` and report note
  `ref_date`, use the device-local calendar date (`YYYY-MM-DD`).
- JSON columns are retained when collections are read and written as an opaque
  whole. They should be normalized only when the product needs relational
  filtering or joins over their elements.

## History-preserving deletion

Deleting a task must not delete historical focus sessions or focus overrides.
The database intentionally does not use cascading foreign keys for those
relationships. Analytics therefore keeps left-join behavior for sessions whose
task has been deleted.

## Migration policy

RN compatibility migrations remain manual and are tested. Future Room-only
changes may use AutoMigration only when no rename, transformation, or legacy
compatibility handling is required. Destructive migration is not allowed.

## Derived phone-behaviour tables (schema 11)

Schema 11 adds three tables: `phone_day_rollup` (one row per local date: screen-on
hours, wake-ups, unlocks, sessions, first unlock, status), `phone_first_app_day`
(which app was opened first after unlocks, per date and package) and
`blocked_attempt_day` (blocked-app attempts per date and package, with an hourly
split). They were added with `AutoMigration(10, 11)`; no manual migration was
needed.

These tables are **derived analytics, not part of the `.focusflow` backup**. They
are rebuilt from the OS usage events (about the last two weeks on first run, then
incrementally) and from the temptation log, so a restore on the same or a new
phone repopulates them from whatever history the phone still has. History the OS
no longer holds cannot be recovered, and those days stay `MISSING`.

Retention: `phone_day_rollup` rows are kept for 365 days; the existing
`usage_rollup_*` tables stay at 90 days. Blocked attempts are merged with
`max(stored, incoming)` per date and package, so the 500-entry temptation log
losing old entries can never lower a stored count.

