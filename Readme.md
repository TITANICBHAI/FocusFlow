<div align="center">
  <img src="docs/logo-transparent.png" alt="FocusFlow logo" width="104" />
  <h1>FocusFlow</h1>
  <p><strong>Make room for focus. Keep distractions out.</strong></p>
  <p>Plan focused time, block distracting apps, and build routines that work for you — right on your Android phone.</p>
  <p>
    <a href="https://github.com/TITANICBHAI/FocusFlow/releases/latest"><strong>Get FocusFlow for Android</strong></a>
    &nbsp;·&nbsp;
    <a href="https://focusflowapp.pages.dev/">Visit the website</a>
  </p>
</div>

<p align="center">
  <a href="https://github.com/TITANICBHAI/FocusFlow/releases"><img src="https://img.shields.io/github/v/release/TITANICBHAI/FocusFlow?label=latest%20release" alt="Latest release" /></a>
  <a href="https://github.com/TITANICBHAI/FocusFlow/actions/workflows/build-native-kotlin-release.yml"><img src="https://github.com/TITANICBHAI/FocusFlow/actions/workflows/build-native-kotlin-release.yml/badge.svg" alt="Build native Android release" /></a>
  <img src="https://img.shields.io/badge/platform-Android-3ddc84" alt="Android app" />
</p>

## Focus that fits your day

FocusFlow is a free Android focus and app-blocking app. Schedule a work session, set limits for distracting apps, or keep selected apps blocked around the clock. Its protections use Android’s built-in services and keep your focus setup and reports on your device.

### What you can do

- **Plan focus sessions** around tasks and schedules, with a countdown and reminders.
- **Block apps your way** with task-based, timed, scheduled, or always-on blocks.
- **Set daily allowances** for selected apps using open-count, time-budget, or interval limits.
- **Block distractions at different levels** with keyword controls and optional local VPN app blocking.
- **Review your patterns** with on-device usage stats and focus reports.
- **Add protection** with PIN controls and Android system safeguards.

Requires Android 10 or later. No root required.

## Screenshots

The screenshots below are framed and cropped to remove the phone’s status and system navigation bars. Profile details are redacted. **The Stats image uses clearly labeled illustrative data, not measured results.**

<table>
  <tr>
    <td align="center"><img src="docs/images/readme/focus-home.png" width="190" alt="Focus tab with options to start a scheduled or standalone focus block" /><br /><sub>Start focus or block apps</sub></td>
    <td align="center"><img src="docs/images/readme/schedule.png" width="190" alt="Schedule tab with a focus task and countdown" /><br /><sub>Plan tasks and sessions</sub></td>
    <td align="center"><img src="docs/images/readme/focus-session.png" width="190" alt="Focus task screen with session controls and progress" /><br /><sub>Keep a session on track</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/images/readme/defense.png" width="190" alt="Defense settings for always-on blocking, daily allowances, keywords, schedules, VPN, and PIN protection" /><br /><sub>Choose your protections</sub></td>
    <td align="center"><img src="docs/images/readme/active-block.png" width="190" alt="Active standalone block with remaining time and extension controls" /><br /><sub>Stay with a timed block</sub></td>
    <td align="center"><img src="docs/images/readme/stats-sample.png" width="190" alt="Stats screen with synthetic sample metrics, marked as illustrative" /><br /><sub>Stats · illustrative sample data</sub></td>
  </tr>
  <tr>
    <td colspan="3" align="center"><img src="docs/images/readme/settings-redacted.png" width="190" alt="Settings screen with personal profile details removed" /><br /><sub>Settings · profile details redacted</sub></td>
  </tr>
</table>

## Built natively for Android

FocusFlow is a native Android app built with **Kotlin** and **Jetpack Compose**. It uses Android’s Accessibility Service, foreground services, UsageStats, Device Admin, and optional `VpnService` for its focus and blocking features. Tasks and app data are stored locally with Room/SQLite.

## Get the app

Download the latest APK from [GitHub Releases](https://github.com/TITANICBHAI/FocusFlow/releases/latest). Choose the APK that matches your device; use the universal APK if you are unsure.

## License

FocusFlow is licensed under **CC BY-NC-SA 4.0**. You may share and adapt it with attribution, for non-commercial use, and under the same license.
