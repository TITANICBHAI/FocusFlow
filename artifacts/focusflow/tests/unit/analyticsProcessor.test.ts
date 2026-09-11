import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs';

vi.mock('@/data/database', () => ({
  dbGetEstimationErrors: vi.fn(),
  dbGetSessionsWithOverrideCount: vi.fn(),
  dbGetTasksByHourOfDay: vi.fn(),
  dbGetTasksInDateRange: vi.fn(),
  dbGetWeeklyCompletionRates: vi.fn(),
}));

vi.mock('@/native-modules/GreyoutModule', () => ({
  GreyoutModule: {
    getTemptationLog: vi.fn(async () => []),
  },
}));

vi.mock('@/native-modules/UsageStatsModule', () => ({
  isUsageSummaryAvailable: false,
  isUsageHourlySummaryAvailable: false,
  UsageStatsModule: {
    getUsageSummary: vi.fn(async () => null),
    getHourlyUsageSummary: vi.fn(async () => null),
  },
}));

import {
  buildAnalyticsSnapshot,
  createAnalyticsSnapshot,
  getAnalyticsRange,
  type AnalyticsSourceData,
} from '@/services/analytics/AnalyticsProcessor';
import * as database from '@/data/database';
import { GreyoutModule } from '@/native-modules/GreyoutModule';
import { task } from '../helpers/task';

const source: AnalyticsSourceData = {
  tasks: [
    task('done', '2026-09-07T08:00:00.000Z', '2026-09-07T08:30:00.000Z', { status: 'completed' }),
    task('skip', '2026-09-07T18:00:00.000Z', '2026-09-07T18:30:00.000Z', { status: 'skipped' }),
    task('missed', '2026-09-08T10:00:00.000Z', '2026-09-08T10:30:00.000Z', { status: 'overdue' }),
  ],
  sessions: [
    {
      session_id: 1,
      started_at: '2026-09-07T08:00:00.000Z',
      ended_at: '2026-09-07T08:25:00.000Z',
      override_count: 0,
    },
    {
      session_id: 2,
      started_at: '2026-09-07T18:00:00.000Z',
      ended_at: '2026-09-07T18:30:00.000Z',
      override_count: 2,
    },
  ],
  estimationErrors: [
    { task_id: 'done', planned_minutes: 30, actual_minutes: 25, start_hour: 8 },
    { task_id: 'evening', planned_minutes: 30, actual_minutes: 15, start_hour: 18 },
  ],
  tasksByHour: [
    { hour: 8, total: 1, completed: 1 },
    { hour: 10, total: 1, completed: 0 },
    { hour: 18, total: 1, completed: 0 },
  ],
  weeklyRates: [
    { week_start: '2026-08-30', completed: 2, total: 4 },
    { week_start: '2026-09-06', completed: 1, total: 3 },
  ],
  temptations: [
    { pkg: 'com.example.video', appName: 'Video', timestamp: Date.parse('2026-09-07T18:05:00.000Z') },
    { pkg: 'com.example.video', appName: 'Video', timestamp: Date.parse('2026-09-07T18:06:00.000Z') },
    { pkg: 'com.example.chat', appName: 'Chat', timestamp: Date.parse('2026-09-08T10:05:00.000Z') },
  ],
  previousTemptations: [
    { pkg: 'com.example.video', appName: 'Video', timestamp: Date.parse('2026-09-01T18:05:00.000Z') },
  ],
};

describe('AnalyticsProcessor', () => {
  it('anchors yesterday and a configured week to local calendar boundaries', () => {
    const now = dayjs('2026-09-09T12:00:00.000Z');
    const yesterday = getAnalyticsRange('yesterday', now);
    expect(yesterday.start.format('YYYY-MM-DD')).toBe('2026-09-08');
    expect(yesterday.end.format('YYYY-MM-DD')).toBe('2026-09-08');

    const mondayWeek = getAnalyticsRange('week', now, 1);
    expect(mondayWeek.start.format('YYYY-MM-DD')).toBe('2026-09-07');
    expect(mondayWeek.end.format('YYYY-MM-DD')).toBe('2026-09-13');
  });

  it('builds bounded task, session, blocking, and trend metrics', () => {
    const snapshot = createAnalyticsSnapshot(
      'week',
      getAnalyticsRange('week', dayjs('2026-09-09T12:00:00.000Z'), 1),
      source,
      '2026-09-09T12:00:00.000Z',
    );

    expect(snapshot.tasks).toMatchObject({
      total: 3,
      completed: 1,
      skipped: 1,
      missed: 1,
      firstTaskHour: 8,
      estimationErrorMinutes: [-5],
    });
    expect(snapshot.tasks.byHour[8]).toEqual({ total: 1, completed: 1 });
    expect(snapshot.sessions).toMatchObject({
      total: 2,
      cleanCount: 1,
      avgDurationMinutes: 27.5,
      fastestWindowHour: 18,
      fastestWindowSampleSize: 1,
    });
    expect(snapshot.blocking).toMatchObject({
      totalAttempts: 3,
      peakHour: 18,
      topApp: { appName: 'Video', count: 2 },
    });
    expect(snapshot.blocking.topAppShare).toBeCloseTo(2 / 3);
    expect(snapshot.trends?.completionRatePrev).toBe(0.5);
    expect(snapshot.trends?.completionRateCurr).toBeCloseTo(1 / 3);
    expect(snapshot.trends?.blockingAttemptsPrev).toBe(1);
    expect(snapshot.trends?.weekByWeek).toEqual([
      { weekStart: '2026-08-30', completionRate: 0.5, hasData: true },
      { weekStart: '2026-09-06', completionRate: 1 / 3, hasData: true },
    ]);
  });

  it('does not manufacture a peak hour or top-app share for empty blocking data', () => {
    const empty = createAnalyticsSnapshot(
      'yesterday',
      getAnalyticsRange('yesterday', dayjs('2026-09-09T12:00:00.000Z')),
      { ...source, temptations: [] },
    );
    expect(empty.blocking.peakHour).toBeNull();
    expect(empty.blocking.topApp).toBeNull();
    expect(empty.blocking.topAppShare).toBeNull();
  });

  it('averages raw hourly UsageStats milliseconds and selects phone-use standouts', () => {
    const now = dayjs('2026-09-09T12:00:00.000Z');
    const range = getAnalyticsRange('three_months', now);
    const days = 90;
    const hourlyMilliseconds = Array.from({ length: 24 }, () => 0);
    hourlyMilliseconds[9] = days * 120 * 60_000;
    hourlyMilliseconds[18] = days * 60 * 60_000;

    const snapshot = createAnalyticsSnapshot(
      'three_months',
      range,
      {
        ...source,
        usageSummary: {
          totalMinutes: 180,
          apps: [
            {
              packageName: 'com.example.video',
              appName: 'Video',
              foregroundMinutes: 120,
              launchCount: 4,
              lastUsedAt: now.valueOf(),
            },
          ],
        },
        usageHourly: {
          foregroundMillisecondsByHour: hourlyMilliseconds,
          totalForegroundMilliseconds: hourlyMilliseconds.reduce((sum, value) => sum + value, 0),
        },
      },
    );

    expect(snapshot.phoneUsage).toMatchObject({
      peakHour: 9,
      peakPeriod: 'morning',
      heaviestApp: { appName: 'Video', minutes: 120 },
    });
    expect(snapshot.phoneUsage?.byHour[9]).toBe(120);
    expect(snapshot.phoneUsage?.byHour[18]).toBe(60);
  });

  it('zero-fills missing trend weeks without counting them as data', () => {
    const snapshot = createAnalyticsSnapshot(
      'three_months',
      getAnalyticsRange('three_months', dayjs('2026-09-09T12:00:00.000Z')),
      { ...source, weeklyRates: [{ week_start: '2026-09-06', completed: 2, total: 4 }] },
    );

    expect(snapshot.trends?.weekByWeek).toHaveLength(12);
    expect(snapshot.trends?.weekByWeek.at(-1)).toEqual({
      weekStart: '2026-09-06',
      completionRate: 0.5,
      hasData: true,
    });
    expect(snapshot.trends?.weeksWithData).toBe(1);
  });

  it('keeps a usable snapshot when a source query fails', async () => {
    vi.mocked(database.dbGetTasksInDateRange).mockRejectedValueOnce(new Error('database unavailable'));
    vi.mocked(database.dbGetSessionsWithOverrideCount).mockResolvedValueOnce([]);
    vi.mocked(database.dbGetEstimationErrors).mockResolvedValueOnce([]);
    vi.mocked(database.dbGetTasksByHourOfDay).mockResolvedValueOnce([]);
    vi.mocked(database.dbGetWeeklyCompletionRates).mockResolvedValueOnce([]);
    vi.mocked(GreyoutModule.getTemptationLog).mockResolvedValueOnce([]);

    const snapshot = await buildAnalyticsSnapshot('week', {
      now: dayjs('2026-09-09T12:00:00.000Z'),
      weekStartDay: 1,
    });

    expect(snapshot.tasks.total).toBe(0);
    expect(snapshot.sourceHealth?.tasks).toBe('failed');
    expect(snapshot.sourceHealth?.sessions).toBe('loaded');
  });
});