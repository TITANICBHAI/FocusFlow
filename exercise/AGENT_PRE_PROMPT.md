# FocusFlow Subtasks & Rich Import — Agent Pre-Prompt

Use this prompt before working on the plan in this folder.

## Read first

Read these files in full and in this order:

1. `exercise/AGENT_PRE_PROMPT.md`
2. `exercise/PLAN.md`
3. `exercise/TRACKER.md`

The plan is the implementation contract. The tracker is the only progress record for this work. Check current project instructions and the working tree before any authorized implementation; confirm current APIs and behavior rather than assuming the plan's code snapshot is still current. Preserve unrelated user changes and user data. Do not replace or overwrite the existing root `plan.md`.

## Authorization and pause

- Reading and maintaining the three planning documents is allowed. Do not edit app code, tests, build files, settings, or other project behavior until the owner explicitly tells you to start editing/implementation.
- The existence of this plan, a tracker item, or a previous task assignment is not implementation authorization. If the owner has only asked you to read, organize, audit, or prepare, do only that documentation/status work.
- After reading these files, if there is no explicit start instruction, stop without making implementation changes. Briefly confirm the plan and tracker are read and wait for authorization.
- Once authorized, follow the plan's order, phase gates, constraints, and decisions. Stop and report at each phase boundary; do not continue into the next phase until the current phase's “done when” condition and required checks pass.
- Do not silently widen scope or change the plan's contract. If a current-code conflict or an unclear product decision blocks safe progress, record it in the tracker and ask the owner.

## Required work rules

- Work alone. Do not create, invoke, or delegate to sub-agents.
- Before editing, inspect the relevant current code, project instructions, and working tree. Preserve unrelated changes and user data.
- Respect the plan's no-touch list and permitted contact points. Do not change enforcement, accessibility service, VPN, alarms, launcher, scheduling, task completion semantics, or unrelated features.
- Treat replacement imports and any deletion of user data as destructive. Keep replacement disabled by default, preserve explicit confirmation and safety guards, and do not perform destructive actions without owner authorization.
- Keep imports fail-soft; invalid subtask/image data must not reject otherwise-valid tasks or files. Follow the plan's validation and size limits.
- Do not expose, copy into files, or print secrets or credentials.
- Run the verification required by the plan. Never state a test, build, lint check, migration, UI check, or device check passed unless its result was observed. Mark device-dependent work “needs device check” when it cannot be verified in the available environment.
- Do not push to GitHub or start, poll, or monitor GitHub Actions unless the owner explicitly asks. Use GitHub Actions for APK-building verification only when explicitly requested. Otherwise, state plainly which checks were not run.

## Tracker discipline

- At the start of a phase, set it to `IN PROGRESS` and record a dated start note before doing its work.
- After each meaningful change, update the relevant phase notes, status, progress, evidence, and history; do not wait until the phase is complete.
- Tick a checkbox only after its work is complete and verified. Never mark attempted, partial, or unverified work complete.
- For every completed item, record changed files, the exact verification performed, and its observed result.
- If a check fails, leave the affected item unchecked and record the observed failure. Preserve it in the tracker; append the fix and successful rerun when available.
- Record decisions, blockers, and deferred work. Do not erase old evidence or failure history.
- Before finishing, reconcile `exercise/TRACKER.md` with the actual code and test evidence. Leave incomplete items unchecked and explain why.

## Final report

Report completed and open phases, verified progress, changed files, exact evidence, decisions, failures, blockers, deferred work, and checks not run. Do not claim unobserved results.
