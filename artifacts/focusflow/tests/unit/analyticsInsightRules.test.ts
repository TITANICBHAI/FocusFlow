import { describe, expect, it, vi } from 'vitest';

vi.mock('@/data/database', () => ({
  dbGetRecentWeeklyInsightIds: vi.fn(async () => []),
  dbRecordWeeklyInsight: vi.fn(async () => undefined),
}));

import type { AnalyticsSnapshot } from '@/services/analytics/AnalyticsProcessor';
import { buildInsights } from '@/services/analytics/InsightEngine';
import { threeMonthRules } from '@/services/analytics/InsightRules/ThreeMonthRules';
import { weeklyRules } from '@/services/analytics/InsightRules/WeeklyRules';
import { yesterdayRules } from '@/services/analytics/InsightRules/YesterdayRules';
import {
  INSIGHT_VARIANTS,
  renderInsightVariant,
} from '@/services/analytics/InsightTemplates';

function trend(
  rates: number[],
  data = rates.map(() => true),
): NonNullable<AnalyticsSnapshot['trends']> {
  return {
    completionRatePrev: rates.at(-2) ?? null,
    completionRateCurr: rates.at(-1) ?? 0,
    blockingAttemptsPrev: 0,
    blockingAttemptsCurr: 0,
    weekByWeek: rates.map((completionRate, index) => ({
      weekStart: `2026-06-${String(index + 1).padStart(2, '0')}`,
      completionRate,
      hasData: data[index] ?? false,
    })),
    weeksWithData: data.filter(Boolean).length,
  };
}

function snapshot(overrides: Partial<AnalyticsSnapshot> = {}): AnalyticsSnapshot {
  const emptyHours = Object.fromEntries(Array.from({ length: 24 }, (_, hour) => [hour, 0]));
  const emptyTaskHours = Object.fromEntries(
    Array.from({ length: 24 }, (_, hour) => [hour, { total: 0, completed: 0 }]),
  );
  const emptyDays = Object.fromEntries(
    Array.from({ length: 7 }, (_, day) => [day, { total: 0, completed: 0 }]),
  );
  const base: AnalyticsSnapshot = {
    generatedAt: '2026-09-11T12:00:00.000Z',
    window: 'yesterday',
    range: {
      startISO: '2026-09-10T00:00:00.000Z',
      endISO: '2026-09-10T23:59:59.999Z',
    },
    tasks: {
      total: 0,
      completed: 0,
      skipped: 0,
      skippedThisWeek: 0,
      missed: 0,
      resultRows: [],
      byHour: emptyTaskHours,
      byDayOfWeek: emptyDays,
      estimationErrorMinutes: [],
      firstTaskHour: null,
    },
    sessions: {
      total: 0,
      cleanCount: 0,
      totalFocusMinutes: 0,
      byHour: emptyHours,
      byDayOfWeek: { 0: 0, 1: 0, 2: 0, 3: 0, 4: 0, 5: 0, 6: 0 },
      avgDurationMinutes: 0,
      fastestWindowHour: null,
      fastestWindowSampleSize: 0,
      hardestSession: null,
    },
    blocking: {
      totalAttempts: 0,
      byHour: emptyHours,
      byApp: {},
      peakHour: null,
      topApp: null,
      topAppShare: null,
    },
    trends: trend([]),
    sourceHealth: {
      tasks: 'loaded',
      sessions: 'loaded',
      estimationErrors: 'loaded',
      tasksByHour: 'loaded',
      weeklyRates: 'loaded',
      temptations: 'loaded',
    },
  };
  return {
    ...base,
    ...overrides,
    tasks: { ...base.tasks, ...overrides.tasks },
    sessions: { ...base.sessions, ...overrides.sessions },
    blocking: { ...base.blocking, ...overrides.blocking },
  };
}

function ids(snapshotValue: AnalyticsSnapshot, limit?: number): string[] {
  return buildInsights(snapshotValue, limit).map((card) => card.id);
}

describe('analytics insight rule coverage', () => {
  it('keeps every plan rule registered and every sentence variant centralized', () => {
    expect(yesterdayRules.map((rule) => rule.id)).toEqual([
      'YESTERDAY_PERFECT_DAY',
      'YESTERDAY_CLEAN_NO_BLOCKS',
      'YESTERDAY_PEAK_BLOCK_HOUR',
      'YESTERDAY_SINGLE_HARD_SESSION',
      'YESTERDAY_SECOND_SKIP_THIS_WEEK',
      'YESTERDAY_TASK_RESULT',
      'YESTERDAY_NOTHING_NOTABLE',
    ]);
    expect(weeklyRules.map((rule) => rule.id)).toEqual([
      'WEEKLY_VULNERABLE_WINDOW',
      'WEEKLY_ONE_APP',
      'WEEKLY_TOP_APP_MODERATE',
      'WEEKLY_CLEAN_WINDOW',
      'WEEKLY_ESTIMATION_IMPROVING',
      'WEEKLY_UNDERESTIMATING',
      'WEEKLY_OVERESTIMATING',
      'WEEKLY_SHOWED_UP',
      'WEEKLY_BETTER_THAN_LAST',
      'WEEKLY_WORSE_THAN_LAST',
      'WEEKLY_FLAT',
    ]);
    expect(threeMonthRules.map((rule) => rule.id)).toEqual([
      'THREE_MONTH_INSUFFICIENT_DATA',
      'THREE_MONTH_SCHEDULING_HONESTY',
      'THREE_MONTH_PHONE_PEAK',
      'THREE_MONTH_REAL_FOCUS_WINDOW',
      'THREE_MONTH_REAL_PROBLEM_APP',
      'THREE_MONTH_IMPROVING',
      'THREE_MONTH_PLATEAU',
      'THREE_MONTH_NIGHT_PATTERN',
    ]);
    expect(Object.keys(INSIGHT_VARIANTS)).toEqual([
      'YESTERDAY_PERFECT_DAY',
      'YESTERDAY_CLEAN_NO_BLOCKS',
      'YESTERDAY_PEAK_BLOCK_HOUR',
      'YESTERDAY_SECOND_SKIP_THIS_WEEK',
      'YESTERDAY_SINGLE_HARD_SESSION',
      'YESTERDAY_NOTHING_NOTABLE',
      'WEEKLY_VULNERABLE_WINDOW',
      'WEEKLY_CLEAN_WINDOW',
      'WEEKLY_ONE_APP',
      'WEEKLY_TOP_APP_MODERATE',
      'WEEKLY_ESTIMATION_IMPROVING',
      'WEEKLY_UNDERESTIMATING',
      'WEEKLY_OVERESTIMATING',
      'WEEKLY_SHOWED_UP',
      'WEEKLY_BETTER_THAN_LAST',
      'WEEKLY_WORSE_THAN_LAST',
      'WEEKLY_FLAT',
      'WEEKLY_NOTHING_UNUSUAL',
      'THREE_MONTH_PHONE_PEAK',
      'THREE_MONTH_REAL_FOCUS_WINDOW',
      'THREE_MONTH_SCHEDULING_HONESTY',
      'THREE_MONTH_REAL_PROBLEM_APP',
      'THREE_MONTH_IMPROVING',
      'THREE_MONTH_PLATEAU',
      'THREE_MONTH_NIGHT_PATTERN',
      'THREE_MONTH_INSUFFICIENT_DATA',
    ]);
  });

  it('covers yesterday thresholds, baseline results, fallback, and sentiment', () => {
    const perfect = snapshot({
      tasks: {
        ...snapshot().tasks,
        total: 2,
        completed: 2,
        resultRows: [
          { title: 'Deep work', status: 'completed' },
          { title: 'Review', status: 'completed' },
        ],
      },
    });
    expect(ids(perfect)).toEqual([
      'YESTERDAY_PERFECT_DAY',
      'YESTERDAY_TASK_RESULT',
      'YESTERDAY_NOTHING_NOTABLE',
    ]);
    expect(buildInsights(perfect)[1].body).toContain('✓ Deep work');

    const blocked = snapshot({
      blocking: {
        ...snapshot().blocking,
        totalAttempts: 10,
        peakHour: 21,
        byHour: { ...snapshot().blocking.byHour, 21: 10 },
      },
      sessions: {
        ...snapshot().sessions,
        hardestSession: { hour: 21, attempts: 6 },
      },
    });
    const cards = buildInsights(blocked);
    expect(cards.map((card) => card.id)).toContain('YESTERDAY_PEAK_BLOCK_HOUR');
    expect(cards.find((card) => card.id === 'YESTERDAY_PEAK_BLOCK_HOUR')).toMatchObject({
      priority: 75,
      sentiment: 'warning',
    });
    expect(cards.find((card) => card.id === 'YESTERDAY_SINGLE_HARD_SESSION')).toMatchObject({
      priority: 80,
      sentiment: 'neutral',
    });

    const empty = buildInsights(snapshot());
    expect(empty.map((card) => card.id)).toEqual([
      'YESTERDAY_TASK_RESULT',
      'YESTERDAY_NOTHING_NOTABLE',
    ]);
  });

  it('uses the plan priorities and weekly threshold boundaries', () => {
    const weekly = snapshot({
      window: 'week',
      blocking: {
        ...snapshot().blocking,
        totalAttempts: 9,
        byHour: { ...snapshot().blocking.byHour, 18: 9 },
        peakHour: 18,
        topApp: { appName: 'Video', count: 5 },
        topAppShare: 5 / 9,
      },
      tasks: {
        ...snapshot().tasks,
        total: 4,
        byDayOfWeek: { ...snapshot().tasks.byDayOfWeek, 1: { total: 4, completed: 3 } },
        estimationErrorMinutes: [25],
      },
    });
    const cards = buildInsights(weekly);
    expect(cards[0]).toMatchObject({ id: 'WEEKLY_ONE_APP', priority: 88, sentiment: 'warning' });
    expect(cards.find((card) => card.id === 'WEEKLY_VULNERABLE_WINDOW')).toMatchObject({
      priority: 85,
      sentiment: 'warning',
    });
    expect(cards.find((card) => card.id === 'WEEKLY_UNDERESTIMATING')).toMatchObject({
      priority: 72,
      sentiment: 'warning',
    });

    const eightAttempts = snapshot({
      window: 'week',
      tasks: { ...snapshot().tasks, total: 1 },
      blocking: {
        ...snapshot().blocking,
        totalAttempts: 8,
        byHour: { ...snapshot().blocking.byHour, 18: 8 },
        peakHour: 18,
      },
    });
    expect(buildInsights(eightAttempts).find((card) => card.id === 'WEEKLY_VULNERABLE_WINDOW'))
      .toMatchObject({ priority: 85, sentiment: 'neutral' });
  });

  it('covers three-month insufficient-data, improvement, and plateau thresholds', () => {
    const insufficient = snapshot({
      window: 'three_months',
      trends: trend([0.5, 0.5, 0.5], [true, true, true]),
    });
    expect(buildInsights(insufficient)[0]).toMatchObject({
      id: 'THREE_MONTH_INSUFFICIENT_DATA',
      priority: 100,
      sentiment: 'neutral',
    });
    expect(buildInsights(insufficient)[0].body).toBe(
      'Not enough data yet for 3-month patterns. Come back after 1 more weeks.',
    );

    const improving = snapshot({
      window: 'three_months',
      trends: trend(
        [0.2, 0.2, 0.2, 0.2, 0.7, 0.4, 0.6, 0.5, 0.3, 0.4, 0.3, 0.4],
      ),
    });
    expect(ids(improving)).toContain('THREE_MONTH_IMPROVING');

    const falseImprovement = snapshot({
      window: 'three_months',
      trends: trend(
        [0.1, 0.4, 0.4, 0.4, 0.8, 0.8, 0.8, 0.8, 0.42, 0.3, 0.42, 0.3],
      ),
    });
    expect(ids(falseImprovement)).not.toContain('THREE_MONTH_IMPROVING');

    const plateau = snapshot({
      window: 'three_months',
      trends: trend([0.6, 0.6, 0.6, 0.6, 0.6, 0.6, 0.62, 0.6, 0.61, 0.6, 0.62, 0.61]),
    });
    expect(ids(plateau)).toContain('THREE_MONTH_PLATEAU');
  });

  it('formats templates deterministically and replaces every supplied value', () => {
    const seedZero = renderInsightVariant('YESTERDAY_PEAK_BLOCK_HOUR', 0, {
      count: 4,
      peak_hour_label: '9pm',
      peak_count: 3,
    });
    expect(seedZero).toBe('You resisted 4 blocked-app attempts. 9pm was your hardest hour, with 3 of them.');
    expect(renderInsightVariant('YESTERDAY_PEAK_BLOCK_HOUR', 0, {
      count: 4,
      peak_hour_label: '9pm',
      peak_count: 3,
    })).toBe(seedZero);
    expect(renderInsightVariant('YESTERDAY_PEAK_BLOCK_HOUR', 1, {
      count: 4,
      peak_hour_label: '9pm',
      peak_count: 3,
    })).toBe('4 times you reached for something blocked. Most of those — 3 — happened 9pm.');
    expect(seedZero).not.toMatch(/\{[a-z_]+\}/);
  });

  it('does not invent clean or phone-use insights for empty or failed sources', () => {
    const emptyWeek = snapshot({
      window: 'week',
      sourceHealth: {
        ...snapshot().sourceHealth!,
        temptations: 'loaded',
      },
    });
    expect(ids(emptyWeek)).toEqual(['WEEKLY_SHOWED_UP']);

    const failedWeek = snapshot({
      window: 'week',
      sourceHealth: {
        ...snapshot().sourceHealth!,
        temptations: 'failed',
      },
      tasks: { ...snapshot().tasks, total: 2 },
    });
    expect(ids(failedWeek)).not.toContain('WEEKLY_CLEAN_WINDOW');

    const failedThreeMonth = snapshot({
      window: 'three_months',
      trends: trend([0.5, 0.5, 0.5], [true, true, true]),
      phoneUsage: {
        byHour: Object.fromEntries(Array.from({ length: 24 }, (_, hour) => [hour, 0])),
        peakHour: null,
        peakPeriod: null,
        heaviestApp: null,
      },
    });
    expect(ids(failedThreeMonth)).not.toContain('THREE_MONTH_PHONE_PEAK');
  });
});