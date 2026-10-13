# FocusFlow Fixes Phase 5 — Agent Pre-Prompt

Use this prompt before doing work tracked by the v9 plan in this folder.

## Read first

Before inspecting implementation details or editing files, read these documents in full and in this order:

1. `fixes/AGENT_PRE_PROMPT.md`
2. `fixes/PLAN.md`
3. `fixes/TRACKER.md` — this is the main and authoritative tracker for the fixes plan.
4. `allowance/TRACKER.md` — read only as a reference for its tracker instructions/format.

The v9 plan supersedes `allowance/PLAN.md` (v7). The previous v8 plan/tracker are archived; `fixes/TRACKER.md` remains the only progress record for this work. `allowance/TRACKER.md` belongs to a separate workstream: do not copy its statuses, evidence, decisions, or progress into the fixes tracker, and do not update it for fixes work. After reading these documents, read the current project instructions, inspect the relevant current source, and check the working tree. Verify paths, APIs, and behavior against the current repository. Preserve unrelated user changes and user data.

## Authorization and scope

- The plan is the implementation contract, not authorization by itself. Do not begin app-code implementation until the user explicitly authorizes it. If the requested scope or authorization is unclear, stop before changing app code and ask.
- A request to audit, reconcile, or work from the plan/tracker authorizes documentation and status corrections only; do not start planned implementation unless the user explicitly asks for implementation.
- Follow the plan's batch order and acceptance criteria. Do not silently widen scope or change the plan's contract. C5 may start after the A1/A2a/A3/A4 foundation gate to close A2b; C5 must not wait for A2b. All other B/C work waits for that integration and Batch A completion. Do not infer a B1a/B1b early-start exception from an attachment; it must be explicitly authorized and recorded in both active plan and tracker before use.
- Respect the plan's no-touch list: do not touch Gradle files, GitHub Actions, APK builds, the `.focusflow` backup format, or the Linux app unless the user explicitly changes the scope.
- Do not start a later batch before the earlier batch's “done when” condition holds, except for the exact C5/A2b dependency stated above and other explicitly parallel work in the plan.
- Device checks are owner-run and happen only at the end of Batch E. Run one initial full session, then retest failed scenarios only for at most two rounds. Never mark an owner-run check complete.
- Protect the upgrade check: run S2 on a data-bearing pre-v9 installation before uninstalling, clearing data, or doing a fresh install for S1 on that phone.
- Treat task replacement and any other deletion of user data as destructive. Keep replacement disabled by default, preserve explicit confirmation and required safety guards, and do not perform destructive actions without the user's authorization.

## Required working rules

- Work alone. Do not create, invoke, or delegate to sub-agents.
- Run Gradle or Android tests on Replit using the current workflow or scripts under `scripts/`. Test only the production flavor. Do not build APKs on Replit.
- Use GitHub Actions for APK-build verification only when the user explicitly asks. Do not push to GitHub or start, poll, or monitor GitHub Actions unless explicitly asked.
- Do not expose, copy into files, or print secrets or credentials.
- Use behavior tests with fake clocks and event sources. Do not add tests that read Kotlin source files as text. Logic under test must not call the system wall clock directly.
- Respect the plan's no-touch list unless the user explicitly changes the scope.
- Treat task replacement and any other deletion of user data as destructive. Keep replacement disabled by default, preserve explicit confirmation and required safety guards, and do not perform destructive actions without the user's authorization.
- Run Gradle tests, or Android tests(leave apk building out of these two and no apk building on replit) on Replit by using the current workflow or scripts under `scripts/`. If verification is needed, use GitHub Actions only when the user explicitly requests APK-building verification. Otherwise state clearly that those checks were not run.
- You shall run test for only one flavour (meaning the production one's test only) becauses they are fundamentally same thing and hence even if run the same test twice but with diff flavour equals to same results.
- Do not push to GitHub or start, poll, or monitor GitHub Actions unless the user explicitly asks.
- Do not expose, copy into files, or print secrets or credentials.
- The plan is the implementation contract, not authorization by itself. Do not begin app-code implementation until the user explicitly authorizes it. If the requested scope or authorization is unclear, stop before changing app code and ask.

## Tracker discipline — required throughout implementation

- Keep `fixes/TRACKER.md` current; it is the main tracker for this effort. At the start of a batch, mark it in progress in its notes. After each meaningful change, update the relevant notes rather than waiting until the batch is finished.
- Give every item a status, `blocked by`, evidence, and owner-run state. Record decisions in the decision log; label applied defaults “default applied, awaiting owner.”
- Tick a checkbox only when its work is complete and the named evidence has been observed. Never mark unverified, partial, attempted, or owner-run work complete.
- For each completed item, record the changed files, the exact verification performed, and its result. Record decisions, failures, blockers, and deferred work.
- If a check fails, leave the item unchecked. Preserve the failure in the tracker, then record the fix and successful rerun before ticking it. Do not erase failed attempts or blockers.
- Reconcile the tracker with the current code and test evidence before finishing. Leave incomplete work unchecked and explain why. Mark a batch done only when its “done when” condition and required tests and gates pass.

## Final response

Report the batches completed and still open, evidence gathered, decisions, failures, blockers, deferred work, and checks not run. Never claim a build, test, device check, or GitHub Actions run passed unless its result was observed.
