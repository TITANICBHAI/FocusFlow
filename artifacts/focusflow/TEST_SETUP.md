# FocusFlow test setup

## Local installation workaround

The FocusFlow package uses Vitest for deterministic TypeScript unit and service
tests:

```sh
pnpm --filter @workspace/focusflow test
```

This workspace initially failed during dependency linking because the Replit
package firewall rejected the stale transitive tarball `shell-quote@1.8.3`.
The dependency came through `react-devtools-core`, which is part of the
existing Expo/React Native graph. The published `shell-quote` version was
checked before changing anything, and the workspace override was set to the
newer compatible `1.10.0`.

The next install attempt exposed a second stale transitive tarball,
`tar@7.5.13`. Its current published compatible version was checked, and the
workspace override was set to `7.5.22`.

The resulting recovery command was:

```sh
pnpm install --filter @workspace/focusflow --no-frozen-lockfile
pnpm --filter @workspace/focusflow test
```

These overrides are in the root `package.json` because pnpm applies workspace
dependency overrides there. Test files, configuration, and scripts remain
inside `artifacts/focusflow/`.

Do not bypass the package firewall or downgrade to an older tarball. If a
future install fails on another stale transitive package, check its dependency
path and published version first, then document the minimal compatible override
here.

## Verification status

- Vitest is linked and the FocusFlow test command runs normally.
- TypeScript tests cover task lifecycle, scheduler behavior, and password
  utilities.
- The full FocusFlow typecheck currently reports unrelated existing errors in
  `app/changelog.tsx`, `app/onboarding.tsx`, `app/permissions.tsx`, and
  `src/context/AppContext.tsx`; those are not caused by the test setup.