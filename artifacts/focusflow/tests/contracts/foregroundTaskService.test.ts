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
      'foregroundAllowanceExpiry?.let { (pkg, expiryMs) ->',
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
      'timeBudgetPkgs[pkg] ?: return@let',
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
});