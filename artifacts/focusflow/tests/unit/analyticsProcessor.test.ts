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

import {
  createAnalyticsSnapshot,
  getAnalyticsRange,
  type AnalyticsSourceData,
} from '@/services/analytics/AnalyticsProcessor';
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
    { task_id: 'done', planned_minutes: 30, actual_minutes: 25 },
  ],
  tasksByHour: [
    { hour: 8, total: 1, completed: 1 },
    { hour: 10, total: 1, completed: 0 },
    { hour: 18, total: 1, completed: 0 },
  ],
  weeklyRates: [
    { week_start: '2026-08-31', completed: 2, total: 4 },
    { week_start: '2026-09-07', completed: 1, total: 3 },
  ],
  temptations: [
    { pkg: 'com.example.video', appName: 'Video', timestamp: Date.parse('2026-09-07T18:05:00.000Z') },
    { pkg: 'com.example.video', appName: 'Video', timestamp: Date.parse('2026-09-07T18:06:00.000Z') },
    { pkg: 'com.example.chat', appName: 'Chat', timestamp: Date.parse('2026-09-08T10:05:00.000Z') },
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
    });
    expect(snapshot.blocking).toMatchObject({
      totalAttempts: 3,
      peakHour: 18,
      topApp: { appName: 'Video', count: 2 },
    });
    expect(snapshot.blocking.topAppShare).toBeCloseTo(2 / 3);
    expect(snapshot.trends?.completionRatePrev).toBe(0.5);
    expect(snapshot.trends?.completionRateCurr).toBeCloseTo(1 / 3);
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
});