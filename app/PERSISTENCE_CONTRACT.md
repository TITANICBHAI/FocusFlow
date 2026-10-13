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