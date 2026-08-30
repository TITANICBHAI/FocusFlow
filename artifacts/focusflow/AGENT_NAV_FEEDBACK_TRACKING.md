# Navigation Feedback + Double-Press Fix — Tracking

Authoritative source: `AGENT_NAV_FEEDBACK_1788057807906.md`

This tracker keeps the attached navigation-feedback request visible in the
FocusFlow artifact without changing the original task description.

## Implementation checklist

- [ ] Step 1 — Add the guarded `navPush` utility.
- [ ] Step 2 — Add the `useNavPress` loading/press hook.
- [ ] Step 3 — Add loading feedback and disabled behavior to `SettingButton`.
- [ ] Step 4 — Wire `useNavPress` into every `SettingButton` call site.
- [ ] Step 5 — Add loading feedback to `ActiveHeaderButton`.
- [ ] Step 6 — Replace remaining user-initiated navigation pushes with guarded navigation.
- [ ] Step 7 — Defer the Active screen data load until navigation interactions finish.
- [ ] Step 8 — Defer sheet openings by one frame to avoid first-frame stutter.

## Verification checklist

- [ ] Rapid navigation taps create only one route transition.
- [ ] Navigation buttons show immediate loading feedback and disable duplicate taps.
- [ ] Active screen data loads after the transition rather than blocking it.
- [ ] Standalone block and other sheets open without first-frame stutter.
- [ ] Notification-driven navigation is also duplicate-safe.

## Tracking notes

- Status: captured from the attached feedback; implementation not started.
- Keep the original feedback file unchanged as the detailed implementation reference.