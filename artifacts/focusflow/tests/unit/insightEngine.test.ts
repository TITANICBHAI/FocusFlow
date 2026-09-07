import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs';
import { computeDailyAnalysis, computeWeeklyAnalysis } from '@/services/insightEngine';
import { getWeekEnd, getWeekStart } from '@/utils/weekUtils';
import { task } from '../helpers/task';

describe('insightEngine', () => {
  it('detects a meaningful morning versus afternoon completion split', () => {
    const tasks = [
      task('morning-1', '2026-08-24T09:00:00.000Z', '2026-08-24T09:30:00.000Z', { status: 'completed' }),
      task('morning-2', '2026-08-24T10:00:00.000Z', '2026-08-24T10:30:00.000Z', { status: 'completed' }),
      task('afternoon-1', '2026-08-24T15:00:00.000Z', '2026-08-24T15:30:00.000Z'),
      task('afternoon-2', '2026-08-24T16:00:00.000Z', '2026-08-24T16:30:00.000Z'),
    ];

    expect(computeDailyAnalysis(tasks, []).insights.map((insight) => insight.id)).toContain('time-of-day-split');
  });

  it('does not report a split below the detector threshold', () => {
    const tasks = [
      task('morning-1', '2026-08-24T09:00:00.000Z', '2026-08-24T09:30:00.000Z', { status: 'completed' }),
      task('morning-2', '2026-08-24T10:00:00.000Z', '2026-08-24T10:30:00.000Z'),
      task('afternoon-1', '2026-08-24T15:00:00.000Z', '2026-08-24T15:30:00.000Z', { status: 'completed' }),
      task('afternoon-2', '2026-08-24T16:00:00.000Z', '2026-08-24T16:30:00.000Z'),
    ];

    expect(computeDailyAnalysis(tasks, []).insights.map((insight) => insight.id)).not.toContain('time-of-day-split');
  });

  it('ranks recurring skips and leaves empty input deterministic', () => {
    const skipped = [
      task('skip-1', '2026-08-21T10:00:00.000Z', '2026-08-21T10:30:00.000Z', { title: 'Workout', status: 'skipped' }),
      task('skip-2', '2026-08-22T10:00:00.000Z', '2026-08-22T10:30:00.000Z', { title: 'Workout', status: 'skipped' }),
      task('skip-3', '2026-08-23T10:00:00.000Z', '2026-08-23T10:30:00.000Z', { title: 'Workout', status: 'skipped' }),
    ];

    expect(computeDailyAnalysis([], skipped).insights[0]?.id).toBe('recurring-skip');
    expect(computeDailyAnalysis([], []).headline).toBe('Rough day.');
    expect(computeWeeklyAnalysis([], []).insights).toEqual([]);
  });

  it('uses the strongest and weakest day thresholds for weekly analysis', () => {
    const tasks = [
      task('best-1', '2026-08-24T10:00:00.000Z', '2026-08-24T10:30:00.000Z', { status: 'completed' }),
      task('best-2', '2026-08-24T11:00:00.000Z', '2026-08-24T11:30:00.000Z', { status: 'completed' }),
      task('middle', '2026-08-25T11:00:00.000Z', '2026-08-25T11:30:00.000Z', { status: 'completed' }),
      task('worst-1', '2026-08-26T11:00:00.000Z', '2026-08-26T11:30:00.000Z'),
      task('worst-2', '2026-08-26T12:00:00.000Z', '2026-08-26T12:30:00.000Z'),
    ];

    expect(computeWeeklyAnalysis(tasks, []).insights.map((insight) => insight.id)).toContain('best-weakest-day');
  });
});

describe('calendar week boundaries', () => {
  it('anchors a configured Monday week to the supplied date', () => {
    const start = getWeekStart(1, dayjs('2026-09-09'));
    expect(start.format('YYYY-MM-DD')).toBe('2026-09-07');
    expect(getWeekEnd(start).format('YYYY-MM-DD')).toBe('2026-09-13');
  });
});
