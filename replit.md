# Project Notes

## Artifacts
- FocusFlow: Expo mobile app in `artifacts/focusflow`, preview path `/`.
- FocusFlow Commercial Ad: video artifact in `artifacts/focusflow-ad`, preview path `/focusflow-ad/`.
- Canvas mockup sandbox in `artifacts/mockup-sandbox`.

## Recent Changes
- Added a manual GitHub Actions AAB-only Android release workflow at `.github/workflows/build-aab.yml`, alongside the existing APK × 3 + AAB release workflow.
- Hardened FocusFlow onboarding and settings sync paths so optional Android native modules or Samsung battery settings failures do not crash first-run onboarding.
- Daily allowance supports three per-app modes: count, time budget, and interval, enforced by the Android accessibility service through SharedPreferences config.
