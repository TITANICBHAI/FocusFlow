import type { AnalyticsSnapshot } from '../AnalyticsProcessor';
import type { InsightRule } from '../InsightEngine';
import {
  hourLabel,
  renderInsightVariant,
  twoHourWindowLabel,
} from '../InsightTemplates';

function averageEstimationError(snapshot: AnalyticsSnapshot): number | null {
  const errors = snapshot.tasks.estimationErrorMinutes;
  if (errors.length === 0) return null;
  return errors.reduce((sum, value) => sum + value, 0) / errors.length;
}

function bestCleanWindow(snapshot: AnalyticsSnapshot): number | null {
  if (snapshot.sourceHealth?.temptations && snapshot.sourceHealth.temptations !== 'loaded') return null;
  if (
    snapshot.tasks.total === 0 &&
    snapshot.sessions.total === 0 &&
    snapshot.blocking.totalAttempts === 0
  ) {
    return null;
  }
  for (let hour = 0; hour < 24; hour += 1) {
    const nextHour = (hour + 1) % 24;
    if ((snapshot.blocking.byHour[hour] ?? 0) === 0 && (snapshot.blocking.byHour[nextHour] ?? 0) === 0) {
      return hour;
    }
  }
  return null;
}

function daysShownUp(snapshot: AnalyticsSnapshot): number {
  const present = new Set<number>();
  for (const [day, bucket] of Object.entries(snapshot.tasks.byDayOfWeek)) {
    if (bucket.total > 0) present.add(Number(day));
  }
  for (const [day, count] of Object.entries(snapshot.sessions.byDayOfWeek ?? {})) {
    if (count > 0) present.add(Number(day));
  }
  return present.size;
}

export const weeklyRules: readonly InsightRule[] = [
  {
    id: 'WEEKLY_VULNERABLE_WINDOW',
    category: 'resistance',
    condition: (snapshot) => snapshot.blocking.totalAttempts > 0 && snapshot.blocking.peakHour !== null,
    priority: () => 85,
    render: (snapshot, seed) => {
      const hour = snapshot.blocking.peakHour;
      const count = hour === null ? 0 : snapshot.blocking.byHour[hour] ?? 0;
      return {
        id: 'WEEKLY_VULNERABLE_WINDOW',
        category: 'resistance',
        priority: 85,
        headline: `${hourLabel(hour)} is your weakest hour`,
        body: renderInsightVariant('WEEKLY_VULNERABLE_WINDOW', seed, {
          peak_hour_label: hourLabel(hour),
          peak_hour_start: hourLabel(hour),
          peak_hour_end: hourLabel(hour === null ? null : (hour + 1) % 24),
          peak_count: count,
          total: snapshot.blocking.totalAttempts,
        }),
        sentiment: count > 8 ? 'warning' : 'neutral',
      };
    },
  },
  {
    id: 'WEEKLY_ONE_APP',
    category: 'pattern',
    condition: (snapshot) => (snapshot.blocking.topAppShare ?? 0) > 0.5,
    priority: () => 88,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_ONE_APP',
      category: 'pattern',
      priority: 88,
      headline: `${snapshot.blocking.topApp?.appName ?? 'One app'} is the habit`,
      body: renderInsightVariant('WEEKLY_ONE_APP', seed, {
        app_name: snapshot.blocking.topApp?.appName ?? 'one app',
        share: Math.round((snapshot.blocking.topAppShare ?? 0) * 100),
      }),
      sentiment: 'warning',
    }),
  },
  {
    id: 'WEEKLY_TOP_APP_MODERATE',
    category: 'pattern',
    condition: (snapshot) =>
      snapshot.blocking.topApp !== null &&
      (snapshot.blocking.topAppShare ?? 0) <= 0.5,
    priority: () => 60,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_TOP_APP_MODERATE',
      category: 'pattern',
      priority: 60,
      headline: `${snapshot.blocking.topApp?.appName ?? 'An app'} was most common`,
      body: renderInsightVariant('WEEKLY_TOP_APP_MODERATE', seed, {
        app_name: snapshot.blocking.topApp?.appName ?? 'one app',
        count: snapshot.blocking.topApp?.count ?? 0,
      }),
      sentiment: 'neutral',
    }),
  },
  {
    id: 'WEEKLY_CLEAN_WINDOW',
    category: 'positive',
    condition: (snapshot) => bestCleanWindow(snapshot) !== null,
    priority: () => 55,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_CLEAN_WINDOW',
      category: 'positive',
      priority: 55,
      headline: 'A quiet window',
      body: renderInsightVariant('WEEKLY_CLEAN_WINDOW', seed, {
        clean_window: twoHourWindowLabel(bestCleanWindow(snapshot)),
      }),
      sentiment: 'positive',
    }),
  },
  {
    id: 'WEEKLY_ESTIMATION_IMPROVING',
    category: 'task',
    condition: (snapshot) => {
      const average = averageEstimationError(snapshot);
      return average !== null && average >= -5 && average <= 10;
    },
    priority: () => 65,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_ESTIMATION_IMPROVING',
      category: 'task',
      priority: 65,
      headline: 'Your estimates were solid',
      body: renderInsightVariant('WEEKLY_ESTIMATION_IMPROVING', seed),
      sentiment: 'positive',
    }),
  },
  {
    id: 'WEEKLY_UNDERESTIMATING',
    category: 'task',
    condition: (snapshot) => (averageEstimationError(snapshot) ?? 0) > 20,
    priority: () => 72,
    render: (snapshot, seed) => {
      const average = averageEstimationError(snapshot) ?? 0;
      return {
        id: 'WEEKLY_UNDERESTIMATING',
        category: 'task',
        priority: 72,
        headline: 'You are underestimating',
        body: renderInsightVariant('WEEKLY_UNDERESTIMATING', seed, {
          avg_error: Math.round(average),
        }),
        sentiment: 'warning',
      };
    },
  },
  {
    id: 'WEEKLY_OVERESTIMATING',
    category: 'task',
    condition: (snapshot) => (averageEstimationError(snapshot) ?? 0) < -15,
    priority: () => 62,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_OVERESTIMATING',
      category: 'task',
      priority: 62,
      headline: 'Your buffers may be generous',
      body: renderInsightVariant('WEEKLY_OVERESTIMATING', seed, {
        abs_error: Math.round(Math.abs(averageEstimationError(snapshot) ?? 0)),
      }),
      sentiment: 'neutral',
    }),
  },
  {
    id: 'WEEKLY_SHOWED_UP',
    category: 'positive',
    condition: () => true,
    priority: () => 40,
    render: (snapshot, seed) => {
      const days = daysShownUp(snapshot);
      const variantSeed = days === 7 ? 1 : days < 3 ? 2 : seed;
      return {
        id: 'WEEKLY_SHOWED_UP',
        category: 'positive',
        priority: 40,
        headline: `You showed up ${days} of 7 days`,
        body: renderInsightVariant('WEEKLY_SHOWED_UP', variantSeed, { count: days }),
        sentiment: days >= 5 ? 'positive' : days < 3 ? 'warning' : 'neutral',
      };
    },
  },
  {
    id: 'WEEKLY_BETTER_THAN_LAST',
    category: 'trend',
    condition: (snapshot) => {
      const trend = snapshot.trends;
      return Boolean(
        trend &&
        trend.completionRatePrev !== null &&
        trend.completionRateCurr > trend.completionRatePrev + 0.05,
      );
    },
    priority: () => 78,
    render: (snapshot, seed) => {
      const trend = snapshot.trends!;
      const direction = trend.blockingAttemptsCurr < (trend.blockingAttemptsPrev ?? trend.blockingAttemptsCurr)
        ? 'down'
        : trend.blockingAttemptsCurr > (trend.blockingAttemptsPrev ?? trend.blockingAttemptsCurr)
          ? 'up'
          : 'holding steady';
      return {
        id: 'WEEKLY_BETTER_THAN_LAST',
        category: 'trend',
        priority: 78,
        headline: 'A better week',
        body: renderInsightVariant('WEEKLY_BETTER_THAN_LAST', seed, { direction }),
        sentiment: 'positive',
      };
    },
  },
  {
    id: 'WEEKLY_WORSE_THAN_LAST',
    category: 'trend',
    condition: (snapshot) => {
      const trend = snapshot.trends;
      return Boolean(
        trend &&
        trend.completionRatePrev !== null &&
        trend.completionRateCurr < trend.completionRatePrev - 0.1,
      );
    },
    priority: () => 82,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_WORSE_THAN_LAST',
      category: 'trend',
      priority: 82,
      headline: 'A harder week',
      body: renderInsightVariant('WEEKLY_WORSE_THAN_LAST', seed),
      sentiment: 'warning',
    }),
  },
  {
    id: 'WEEKLY_FLAT',
    category: 'nothing_to_report',
    condition: (snapshot) => {
      const trend = snapshot.trends;
      return Boolean(
        trend &&
        trend.completionRatePrev !== null &&
        Math.abs(trend.completionRateCurr - trend.completionRatePrev) < 0.05 &&
        trend.completionRateCurr >= trend.completionRatePrev - 0.1,
      );
    },
    priority: () => 35,
    render: (snapshot, seed) => ({
      id: 'WEEKLY_FLAT',
      category: 'nothing_to_report',
      priority: 35,
      headline: 'Consistent with last week',
      body: renderInsightVariant('WEEKLY_FLAT', seed),
      sentiment: 'neutral',
    }),
  },
];