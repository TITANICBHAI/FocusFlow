import type { AnalyticsSnapshot } from '../AnalyticsProcessor';
import type { InsightRule } from '../InsightEngine';
import {
  hourLabel,
  renderInsightVariant,
} from '../InsightTemplates';

function dayName(day: number): string {
  return ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][day] ?? 'That day';
}

function worstScheduledDay(snapshot: AnalyticsSnapshot): {
  day: number;
  total: number;
  completed: number;
  rate: number;
} | null {
  return Object.entries(snapshot.tasks.byDayOfWeek)
    .map(([day, bucket]) => ({
      day: Number(day),
      total: bucket.total,
      completed: bucket.completed,
      rate: bucket.total > 0 ? bucket.completed / bucket.total : 1,
    }))
    .filter((bucket) => bucket.total > 5 && bucket.rate < 0.4)
    .sort((a, b) => a.rate - b.rate || b.total - a.total || a.day - b.day)[0] ?? null;
}

function hasEnoughTrendData(snapshot: AnalyticsSnapshot, count: number): boolean {
  return (snapshot.trends?.weeksWithData ?? 0) >= count;
}

function lastTrendWeeks(snapshot: AnalyticsSnapshot, count: number) {
  const weeks = snapshot.trends?.weekByWeek ?? [];
  return weeks.length >= count ? weeks.slice(-count) : [];
}

function average(values: number[]): number {
  return values.length === 0
    ? 0
    : values.reduce((sum, value) => sum + value, 0) / values.length;
}

function nightPattern(snapshot: AnalyticsSnapshot): { multiplier: number } | null {
  const byHour = snapshot.phoneUsage?.byHour;
  if (!byHour) return null;
  const daytimeHours = Array.from({ length: 15 }, (_, index) => index + 6);
  const nightHours = [21, 22, 23];
  const daytimeAverage = daytimeHours.reduce((sum, hour) => sum + (byHour[hour] ?? 0), 0) / daytimeHours.length;
  const nightAverage = nightHours.reduce((sum, hour) => sum + (byHour[hour] ?? 0), 0) / nightHours.length;
  if (daytimeAverage <= 0 || nightAverage <= daytimeAverage * 2) return null;
  return { multiplier: Math.round((nightAverage / daytimeAverage) * 10) / 10 };
}

export const threeMonthRules: readonly InsightRule[] = [
  {
    id: 'THREE_MONTH_INSUFFICIENT_DATA',
    category: 'nothing_to_report',
    condition: (snapshot) => (snapshot.trends?.weeksWithData ?? 0) < 4,
    priority: () => 100,
    render: (snapshot, seed) => ({
      id: 'THREE_MONTH_INSUFFICIENT_DATA',
      category: 'nothing_to_report',
      priority: 100,
      headline: 'Not enough history yet',
      body: renderInsightVariant('THREE_MONTH_INSUFFICIENT_DATA', seed, {
        weeks_remaining: Math.max(0, 4 - (snapshot.trends?.weeksWithData ?? 0)),
      }),
      sentiment: 'neutral',
    }),
  },
  {
    id: 'THREE_MONTH_SCHEDULING_HONESTY',
    category: 'pattern',
    condition: (snapshot) => worstScheduledDay(snapshot) !== null,
    priority: () => 95,
    render: (snapshot, seed) => {
      const worst = worstScheduledDay(snapshot)!;
      return {
        id: 'THREE_MONTH_SCHEDULING_HONESTY',
        category: 'pattern',
        priority: 95,
        headline: `${dayName(worst.day)} is not matching the schedule`,
        body: renderInsightVariant('THREE_MONTH_SCHEDULING_HONESTY', seed, {
          day_name: dayName(worst.day),
          rate: Math.round(worst.rate * 100),
        }),
        sentiment: 'warning',
      };
    },
  },
  {
    id: 'THREE_MONTH_PHONE_PEAK',
    category: 'pattern',
    condition: (snapshot) => Boolean(snapshot.phoneUsage?.byHour && snapshot.phoneUsage.peakHour !== null),
    priority: () => 92,
    render: (snapshot, seed) => {
      const phone = snapshot.phoneUsage!;
      const peakHour = phone.peakHour;
      return {
        id: 'THREE_MONTH_PHONE_PEAK',
        category: 'pattern',
        priority: 92,
        headline: `${phone.peakPeriod ? phone.peakPeriod[0].toUpperCase() + phone.peakPeriod.slice(1) : 'Phone use'} is your peak`,
        body: renderInsightVariant('THREE_MONTH_PHONE_PEAK', seed, {
          peak_start: hourLabel(peakHour),
          peak_end: hourLabel(peakHour === null ? null : (peakHour + 1) % 24),
          peak_period_label: phone.peakPeriod ?? 'That period',
        }),
        sentiment: 'neutral',
      };
    },
  },
  {
    id: 'THREE_MONTH_REAL_FOCUS_WINDOW',
    category: 'positive',
    condition: (snapshot) =>
      snapshot.sessions.fastestWindowHour !== null &&
      snapshot.sessions.fastestWindowSampleSize > 20,
    priority: () => 90,
    render: (snapshot, seed) => ({
      id: 'THREE_MONTH_REAL_FOCUS_WINDOW',
      category: 'positive',
      priority: 90,
      headline: 'Your sharpest window',
      body: renderInsightVariant('THREE_MONTH_REAL_FOCUS_WINDOW', seed, {
        hour_label: hourLabel(snapshot.sessions.fastestWindowHour),
        pct: snapshot.sessions.fastestWindowImprovementPercent ?? 0,
      }),
      sentiment: 'positive',
    }),
  },
  {
    id: 'THREE_MONTH_REAL_PROBLEM_APP',
    category: 'resistance',
    condition: (snapshot) => (snapshot.blocking.topAppShare ?? 0) > 0.4,
    priority: () => 88,
    render: (snapshot, seed) => ({
      id: 'THREE_MONTH_REAL_PROBLEM_APP',
      category: 'resistance',
      priority: 88,
      headline: `${snapshot.blocking.topApp?.appName ?? 'One app'} is the problem`,
      body: renderInsightVariant('THREE_MONTH_REAL_PROBLEM_APP', seed, {
        app_name: snapshot.blocking.topApp?.appName ?? 'One app',
        share: Math.round((snapshot.blocking.topAppShare ?? 0) * 100),
      }),
      sentiment: 'warning',
    }),
  },
  {
    id: 'THREE_MONTH_IMPROVING',
    category: 'trend',
    condition: (snapshot) => {
      if (!hasEnoughTrendData(snapshot, 4)) return false;
      const weeks = snapshot.trends?.weekByWeek ?? [];
      const firstFour = weeks.slice(0, 4);
      const lastFour = lastTrendWeeks(snapshot, 4);
      if (
        firstFour.length !== 4 ||
        lastFour.length !== 4 ||
        firstFour.some((week) => !week.hasData) ||
        lastFour.some((week) => !week.hasData)
      ) {
        return false;
      }
      const monotonic = lastFour.length === 4 &&
        lastFour.every((week, index) => index === 0 || week.completionRate >= lastFour[index - 1].completionRate);
      const netImprovement =
        average(lastFour.map((week) => week.completionRate)) -
          average(firstFour.map((week) => week.completionRate)) >=
        0.1;
      return monotonic || netImprovement;
    },
    priority: () => 85,
    render: (snapshot, seed) => {
      const rates = snapshot.trends?.weekByWeek.map((week) => week.completionRate) ?? [];
      return {
        id: 'THREE_MONTH_IMPROVING',
        category: 'trend',
        priority: 85,
        headline: 'You are getting better at this',
        body: renderInsightVariant('THREE_MONTH_IMPROVING', seed, {
          delta: Math.round(((rates.at(-1) ?? 0) - (rates[0] ?? 0)) * 100),
        }),
        sentiment: 'positive',
      };
    },
  },
  {
    id: 'THREE_MONTH_PLATEAU',
    category: 'trend',
    condition: (snapshot) => {
      if (!hasEnoughTrendData(snapshot, 6)) return false;
      const lastSix = lastTrendWeeks(snapshot, 6);
      if (lastSix.length !== 6 || lastSix.some((week) => !week.hasData)) return false;
      const rates = lastSix.map((week) => week.completionRate);
      return Math.max(...rates) - Math.min(...rates) < 0.05;
    },
    priority: () => 75,
    render: (snapshot, seed) => {
      const rates = lastTrendWeeks(snapshot, 6).map((week) => week.completionRate);
      const average = rates.length ? rates.reduce((sum, rate) => sum + rate, 0) / rates.length : 0;
      return {
        id: 'THREE_MONTH_PLATEAU',
        category: 'trend',
        priority: 75,
        headline: 'A steady plateau',
        body: renderInsightVariant('THREE_MONTH_PLATEAU', seed, {
          rate: Math.round(average * 100),
        }),
        sentiment: 'neutral',
      };
    },
  },
  {
    id: 'THREE_MONTH_NIGHT_PATTERN',
    category: 'pattern',
    condition: (snapshot) => nightPattern(snapshot) !== null,
    priority: () => 80,
    render: (snapshot, seed) => ({
      id: 'THREE_MONTH_NIGHT_PATTERN',
      category: 'pattern',
      priority: 80,
      headline: 'A late-night pattern',
      body: renderInsightVariant('THREE_MONTH_NIGHT_PATTERN', seed, {
        mult: nightPattern(snapshot)?.multiplier ?? 0,
      }),
      sentiment: 'neutral',
    }),
  },
];