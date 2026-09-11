import { describe, expect, it, vi } from 'vitest';

vi.mock('@/data/database', () => ({
  dbGetEarnedAchievementIds: vi.fn(async () => []),
  dbRecordEarnedAchievements: vi.fn(async () => undefined),
}));

import { evaluateAchievements, ACHIEVEMENTS } from '@/services/analytics/AchievementEngine';
import type { AnalyticsSnapshot } from '@/services/analytics/AnalyticsProcessor';
import type { LifetimeStats } from '@/data/database';

const lifetime: LifetimeStats = {
  completedTasks: 20,
  totalSessions: 12,
  cleanSessions: 10,
  totalFocusMinutes: 90,
  totalOverrideAttempts: 10,
  currentStreakDays: 4,
};

const snapshot: AnalyticsSnapshot = {
  generatedAt: '2026-09-09T12:00:00.000Z',
  window: 'week',
  range: { startISO: '2026-09-07T00:00:00.000Z', endISO: '2026-09-13T23:59:59.999Z' },
  tasks: {
    total: 7,
    completed: 6,
    skipped: 1,
    missed: 0,
    byHour: {},
    byDayOfWeek: {
      0: { total: 1, completed: 1 },
      1: { total: 1, completed: 1 },
      2: { total: 1, completed: 1 },
      3: { total: 1, completed: 1 },
      4: { total: 1, completed: 1 },
      5: { total: 1, completed: 1 },
      6: { total: 1, completed: 0 },
    },
    estimationErrorMinutes: [5, -5, 8, -4, 2],
    firstTaskHour: 8,
  },
  sessions: {
    total: 1,
    cleanCount: 1,
    totalFocusMinutes: 60,
    byHour: {},
    avgDurationMinutes: 60,
    fastestWindowHour: null,
  },
  blocking: {
    totalAttempts: 0,
    byHour: {},
    byApp: {},
    peakHour: null,
    topApp: null,
    topAppShare: null,
  },
  trends: {
    completionRatePrev: 0.5,
    completionRateCurr: 0.85,
    blockingAttemptsPrev: null,
    blockingAttemptsCurr: 0,
    weekByWeek: [],
  },
};

describe('AchievementEngine', () => {
  it('evaluates achievements from bounded lifetime and window data', () => {
    const earned = evaluateAchievements(lifetime, snapshot).map((item) => item.id);
    expect(earned).toEqual(expect.arrayContaining([
      'RESISTANCE_10_CLEAN_SESSIONS',
      'HONEST_ESTIMATOR',
      'PRESENCE_7_DAYS',
      'QUIET_WIN',
    ]));
    expect(earned).not.toContain('PATTERN_BREAKER');
  });

  it('keeps the hidden achievement in the source registry but marks it hidden', () => {
    expect(ACHIEVEMENTS.find((item) => item.id === 'QUIET_WIN')?.hidden).toBe(true);
  });
});