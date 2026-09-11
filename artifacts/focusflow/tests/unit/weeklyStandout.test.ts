import { describe, expect, it, vi } from 'vitest';

vi.mock('@/data/database', () => ({
  dbGetRecentWeeklyInsightIds: vi.fn(async () => []),
  dbRecordWeeklyInsight: vi.fn(async () => undefined),
}));

import { selectWeeklyStandout } from '@/services/analytics/InsightEngine';
import type { AnalyticsSnapshot } from '@/services/analytics/AnalyticsProcessor';

const snapshot: AnalyticsSnapshot = {
  generatedAt: '2026-09-09T12:00:00.000Z',
  window: 'week',
  range: { startISO: '2026-09-07T00:00:00.000Z', endISO: '2026-09-13T23:59:59.999Z' },
  tasks: {
    total: 8,
    completed: 4,
    skipped: 4,
    missed: 0,
    byHour: {},
    byDayOfWeek: {
      0: { total: 1, completed: 1 },
      1: { total: 1, completed: 1 },
      2: { total: 1, completed: 0 },
      3: { total: 1, completed: 0 },
      4: { total: 1, completed: 0 },
      5: { total: 1, completed: 0 },
      6: { total: 2, completed: 1 },
    },
    estimationErrorMinutes: [],
    firstTaskHour: 9,
  },
  sessions: {
    total: 2,
    cleanCount: 1,
    totalFocusMinutes: 50,
    byHour: {},
    avgDurationMinutes: 25,
    fastestWindowHour: null,
  },
  blocking: {
    totalAttempts: 3,
    byHour: { 18: 3 },
    byApp: { video: { appName: 'Video', count: 2 } },
    peakHour: 18,
    topApp: { appName: 'Video', count: 2 },
    topAppShare: 2 / 3,
  },
  trends: {
    completionRatePrev: 0.4,
    completionRateCurr: 0.5,
    blockingAttemptsPrev: null,
    blockingAttemptsCurr: 3,
    weekByWeek: [],
  },
};

describe('weekly standout selection', () => {
  it('selects the highest-priority new signal', () => {
    expect(selectWeeklyStandout(snapshot, []).id).toBe('WEEKLY_ONE_APP');
  });

  it('falls back to nothing unusual when every signal was already used', () => {
    expect(selectWeeklyStandout(snapshot, ['WEEKLY_ONE_APP', 'WEEKLY_VULNERABLE_WINDOW', 'WEEKLY_SHOWED_UP']).id)
      .toBe('WEEKLY_NOTHING_UNUSUAL');
  });
});