# FocusFlow Database Logging v3 — Tracking

Authoritative source: `AGENT_DB_LOGGING_V3_1788058109007.md`

Dependency: apply the persistence plan's Phase 1/2 database behavior first;
logging should describe that behavior rather than preserve removed fallback
paths.

## Logging implementation checklist

- [x] 1. Add the successful-startup `[DB_READY]` health snapshot.
- [x] 2. Log `markUnrecoverable()` transitions with structured reasons.
- [x] 3. Add JSI probe/retry outcome logs.
- [x] 4. Log `resetDb()` state before clearing it.
- [x] 5. Add slow-operation timing to `runWithDb` and `runWithDbOr`.
- [x] 6. Add affected-row logging for the listed critical writes.
- [x] 7. Log WAL checkpoint outcomes and busy state.
- [x] 8. Add write-queue depth and wait-time telemetry.
- [x] 9. Add migration start, success, and failure logs.

## Privacy and logging rules

- [x] Do not log task titles, descriptions, notes, or settings values.
- [x] Do not log SQL parameter values or full stack traces.
- [x] Do not log package names in the JS database logs.
- [x] Keep the database logger tag as `database`.
- [x] Do not use `console.log` or modify `startupLogger.ts`.
- [x] Keep SharedPrefs native logging separate from JS database logging.

## Device verification checklist

- [ ] `[DB_READY]` appears with task/session counts and startup timing.
- [ ] WAL checkpoint outcome is visible, including blocked-lock warnings.
- [ ] Critical writes report affected rows.
- [ ] JSI failures use `[DB_UNAVAILABLE]`, not removed recovery tags.
- [ ] Retry logs show the expected retry/probe/success or failure sequence.
- [ ] Review logs for accidental user-content or sensitive-value leakage.

## Tracking notes

- Status: database logging implementation is complete in `src/data/database.ts`;
  the dependent Phase 1 persistence behavior is present; device verification
  remains pending.
- Keep the original logging plan unchanged as the detailed reference.
- API 30/API 31 log-sequence verification was not claimed because no Android devices are available in this workspace.
