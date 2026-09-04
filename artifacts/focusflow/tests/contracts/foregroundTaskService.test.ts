import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const foregroundTaskService = readFileSync(
  path.resolve(
    __dirname,
    '../../android-native/app/src/main/java/com/tbtechs/focusflow/services/ForegroundTaskService.kt',
  ),
  'utf8',
);

describe('ForegroundTaskService allowance expiry contract', () => {
  it('schedules the declared foreground allowance expiry variable', () => {
    expect(foregroundTaskService).toContain(
      'var foregroundAllowanceExpiry: AllowanceExpiry? = null',
    );
    expect(foregroundTaskService).toContain(
      'foregroundAllowanceExpiry = AllowanceExpiry(',
    );
    expect(foregroundTaskService).toContain(
      'foregroundAllowanceExpiry?.let { expiry ->',
    );
    expect(foregroundTaskService).not.toContain('foregroundBudgetExpiry');
  });

  it('keeps immediate expiry limited to daily time budgets', () => {
    const scheduleStart = foregroundTaskService.indexOf(
      'foregroundAllowanceExpiry?.let',
    );
    const scheduleEnd = foregroundTaskService.indexOf(
      '\n    }\n\n    /**',
      scheduleStart,
    );
    const scheduleSource = foregroundTaskService.slice(scheduleStart, scheduleEnd);

    expect(scheduleSource).toContain(
      'timeBudgetPkgs[expiry.pkg] ?: return@let',
    );
    expect(scheduleSource).toContain(
      'if (expiry.mode != "time_budget")',
    );
    expect(scheduleSource).toContain(
      'Interval allowances are enforced by the AccessibilityService',
    );
    expect(scheduleSource).toContain(
      'if (!hasFreshActiveAllowanceSession(expiry.pkg, System.currentTimeMillis()))',
    );
    expect(scheduleSource).toContain(
      'expiry.sessionOpenAtMs',
    );
    expect(scheduleSource).not.toContain('intervalPkgs[pkg]');
  });

  it('uses window-scoped UsageEvents for interval allowance accounting', () => {
    expect(foregroundTaskService).toContain(
      'private fun queryIntervalUsageMs(',
    );
    expect(foregroundTaskService).toContain(
      'UsageEvents.Event.ACTIVITY_RESUMED',
    );
    expect(foregroundTaskService).toContain(
      'UsageEvents.Event.ACTIVITY_PAUSED',
    );
    expect(foregroundTaskService).toContain(
      'val intervalUsageSamples = intervalWindowStarts.mapValues',
    );
    expect(foregroundTaskService).toContain(
      'intervalUsageSample?.windowStartMs != windowStartMs',
    );
    expect(foregroundTaskService).not.toContain(
      'UsageStatsManager.INTERVAL_DAILY,\n                            windowStartMs',
    );
  });

  it('uses window-scoped UsageEvents for time-budget fallback accounting', () => {
    expect(foregroundTaskService).toContain(
      'queryUsageEventsForegroundMs(usm, pkg, startOfDay, now)',
    );
    expect(foregroundTaskService).not.toContain(
      'statsMap[pkg]?.totalTimeInForeground',
    );
    expect(foregroundTaskService).toContain(
      'Treat another package resuming as an app switch',
    );
  });

  it('invalidates a stale fallback timer when the allowance session changes', () => {
    expect(foregroundTaskService).toContain(
      'currentSessionOpenAtMs != sessionOpenAtMs',
    );
    expect(foregroundTaskService).toContain(
      'else if (sessionOpenAtMs > 0L)',
    );
    expect(foregroundTaskService).toContain(
      'A timer scheduled for an older session must not exhaust',
    );
  });
});