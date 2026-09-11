import dayjs, { type Dayjs } from 'dayjs';
import type { Task } from '@/data/types';
import {
  dbGetEstimationErrors,
  dbGetSessionsWithOverrideCount,
  dbGetTasksByHourOfDay,
  dbGetTasksInDateRange,
  dbGetWeeklyCompletionRates,
  type EstimationErrorRow,
  type SessionOverrideCountRow,
  type TasksByHourRow,
  type WeeklyCompletionRateRow,
} from '@/data/database';
import { GreyoutModule, type TemptationEntry } from '@/native-modules/GreyoutModule';
import {
  isUsageHourlySummaryAvailable,
  isUsageSummaryAvailable,
  UsageStatsModule,
  type UsageHourlySummary,
  type UsageSummary,
} from '@/native-modules/UsageStatsModule';

export type AnalyticsWindow = 'yesterday' | 'week' | 'three_months';

export interface AnalyticsSnapshot {
  generatedAt: string;
  window: AnalyticsWindow;
  range: { startISO: string; endISO: string };
  tasks: {
    total: number;
    completed: number;
    skipped: number;
    skippedThisWeek?: number;
    missed: number;
    resultRows?: { title: string; status: Task['status'] }[];
    byHour: Record<number, { total: number; completed: number }>;
    byDayOfWeek: Record<number, { total: number; completed: number }>;
    estimationErrorMinutes: number[];
    firstTaskHour: number | null;
  };
  sessions: {
    total: number;
    cleanCount: number;
    totalFocusMinutes: number;
    byHour: Record<number, number>;
    byDayOfWeek?: Record<number, number>;
    avgDurationMinutes: number;
    fastestWindowHour: number | null;
    fastestWindowSampleSize: number;
    fastestWindowImprovementPercent?: number;
    hardestSession?: { hour: number; attempts: number } | null;
  };
  blocking: {
    totalAttempts: number;
    byHour: Record<number, number>;
    byApp: Record<string, { appName: string; count: number }>;
    peakHour: number | null;
    topApp: { appName: string; count: number } | null;
    topAppShare: number | null;
  };
  trends?: {
    completionRatePrev: number | null;
    completionRateCurr: number;
    blockingAttemptsPrev: number | null;
    blockingAttemptsCurr: number;
    weekByWeek: { weekStart: string; completionRate: number; hasData: boolean }[];
    weeksWithData: number;
  };
  sourceHealth?: AnalyticsSourceHealth;
  phoneUsage?: {
    byHour: Record<number, number>;
    peakHour: number | null;
    peakPeriod: 'morning' | 'afternoon' | 'evening' | 'night' | null;
    heaviestApp: { appName: string; minutes: number } | null;
  };
}

export type AnalyticsSourceState = 'loaded' | 'unavailable' | 'failed';

export interface AnalyticsSourceHealth {
  tasks: AnalyticsSourceState;
  sessions: AnalyticsSourceState;
  estimationErrors: AnalyticsSourceState;
  tasksByHour: AnalyticsSourceState;
  weeklyRates: AnalyticsSourceState;
  temptations: AnalyticsSourceState;
  usageSummary?: AnalyticsSourceState;
  usageHourly?: AnalyticsSourceState;
}

export interface AnalyticsSourceData {
  tasks: Task[];
  tasksThisWeek?: Task[];
  sessions: SessionOverrideCountRow[];
  estimationErrors: EstimationErrorRow[];
  tasksByHour: TasksByHourRow[];
  weeklyRates: WeeklyCompletionRateRow[];
  temptations: TemptationEntry[];
  previousTemptations?: TemptationEntry[];
  usageSummary?: UsageSummary | null;
  usageHourly?: UsageHourlySummary | null;
  health?: AnalyticsSourceHealth;
}

export interface AnalyticsRange {
  start: Dayjs;
  end: Dayjs;
  trendWeekAnchor?: Dayjs;
}

export function getAnalyticsRange(
  window: AnalyticsWindow,
  now: Dayjs = dayjs(),
  weekStartDay = 0,
): AnalyticsRange {
  if (window === 'yesterday') {
    const day = now.subtract(1, 'day');
    return {
      start: day.startOf('day'),
      end: day.endOf('day'),
      trendWeekAnchor: day.startOf('week'),
    };
  }
  if (window === 'three_months') {
    return {
      start: now.subtract(89, 'day').startOf('day'),
      end: now.endOf('day'),
      trendWeekAnchor: now.startOf('week'),
    };
  }
  const offset = (now.day() - weekStartDay + 7) % 7;
  const start = now.subtract(offset, 'day').startOf('day');
  return {
    start,
    end: start.add(6, 'day').endOf('day'),
    trendWeekAnchor: start
      .subtract(weekStartDay === 0 ? 0 : 1, 'day')
      .startOf('week'),
  };
}

function emptyHourBuckets(): Record<number, number> {
  return Object.fromEntries(Array.from({ length: 24 }, (_, hour) => [hour, 0]));
}

function emptyTaskHourBuckets(): Record<number, { total: number; completed: number }> {
  return Object.fromEntries(
    Array.from({ length: 24 }, (_, hour) => [hour, { total: 0, completed: 0 }]),
  );
}

function emptyDayBuckets(): Record<number, { total: number; completed: number }> {
  return Object.fromEntries(
    Array.from({ length: 7 }, (_, day) => [day, { total: 0, completed: 0 }]),
  );
}

function buildTaskMetrics(
  tasks: Task[],
  estimationErrors: EstimationErrorRow[],
  taskHourRows: TasksByHourRow[],
  tasksThisWeek?: Task[],
): AnalyticsSnapshot['tasks'] {
  const byHour = emptyTaskHourBuckets();
  const byDayOfWeek = emptyDayBuckets();
  const resultRows = tasks.map((task) => ({ title: task.title, status: task.status }));
  let firstTaskHour: number | null = null;
  let firstTaskAt = Number.POSITIVE_INFINITY;

  for (const task of tasks) {
    const date = new Date(task.startTime);
    if (Number.isNaN(date.getTime())) continue;
    const hour = date.getHours();
    const day = date.getDay();
    byHour[hour].total += 1;
    byHour[hour].completed += task.status === 'completed' ? 1 : 0;
    byDayOfWeek[day].total += 1;
    byDayOfWeek[day].completed += task.status === 'completed' ? 1 : 0;
    if (date.getTime() < firstTaskAt) {
      firstTaskAt = date.getTime();
      firstTaskHour = hour;
    }
  }

  // Prefer SQLite's local-time grouping when available. It remains empty on
  // platforms/test doubles that do not expose the query, so the task-derived
  // buckets above are still a complete fallback.
  for (const row of taskHourRows) {
    if (row.hour < 0 || row.hour > 23) continue;
    byHour[row.hour] = { total: row.total, completed: row.completed };
  }

  const taskIds = new Set(tasks.map((task) => task.id));
  return {
    total: tasks.length,
    completed: tasks.filter((task) => task.status === 'completed').length,
    skipped: tasks.filter((task) => task.status === 'skipped').length,
    skippedThisWeek: (tasksThisWeek ?? tasks).filter((task) => task.status === 'skipped').length,
    missed: tasks.filter((task) => task.status === 'overdue').length,
    resultRows,
    byHour,
    byDayOfWeek,
    estimationErrorMinutes: estimationErrors
      .filter((row) => taskIds.has(row.task_id))
      .map((row) => row.actual_minutes - row.planned_minutes)
      .filter(Number.isFinite),
    firstTaskHour,
  };
}

function buildSessionMetrics(
  sessions: SessionOverrideCountRow[],
  estimationErrors: EstimationErrorRow[],
): AnalyticsSnapshot['sessions'] {
  const byHour = emptyHourBuckets();
  const byDayOfWeek = Object.fromEntries(Array.from({ length: 7 }, (_, day) => [day, 0]));
  let totalFocusMinutes = 0;
  const durations: number[] = [];
  const ratiosByHour = new Map<number, number[]>();
  let hardestSession: { hour: number; attempts: number } | null = null;

  for (const session of sessions) {
    const start = new Date(session.started_at);
    if (Number.isNaN(start.getTime())) continue;
    byHour[start.getHours()] += 1;
    byDayOfWeek[start.getDay()] += 1;
    if (!hardestSession || session.override_count > hardestSession.attempts) {
      hardestSession = { hour: start.getHours(), attempts: session.override_count };
    }
    const end = session.ended_at ? new Date(session.ended_at) : new Date();
    const duration = Math.max(0, (end.getTime() - start.getTime()) / 60000);
    totalFocusMinutes += duration;
    durations.push(duration);
  }

  for (const row of estimationErrors) {
    const hour = row.start_hour;
    if (
      hour === undefined ||
      hour === null ||
      hour < 0 ||
      hour > 23 ||
      !Number.isFinite(row.planned_minutes) ||
      row.planned_minutes <= 0 ||
      !Number.isFinite(row.actual_minutes) ||
      row.actual_minutes < 0
    ) {
      continue;
    }
    const ratios = ratiosByHour.get(hour) ?? [];
    ratios.push(row.actual_minutes / row.planned_minutes);
    ratiosByHour.set(hour, ratios);
  }

  const fastestWindow = [...ratiosByHour.entries()]
    .map(([hour, ratios]) => ({
      hour,
      sampleSize: ratios.length,
      averageRatio: ratios.reduce((sum, ratio) => sum + ratio, 0) / ratios.length,
    }))
    .sort((a, b) => a.averageRatio - b.averageRatio || b.sampleSize - a.sampleSize || a.hour - b.hour)[0];
  const nextFastestWindow = [...ratiosByHour.entries()]
    .map(([hour, ratios]) => ({
      hour,
      averageRatio: ratios.reduce((sum, ratio) => sum + ratio, 0) / ratios.length,
    }))
    .sort((a, b) => a.averageRatio - b.averageRatio || a.hour - b.hour)[1];

  return {
    total: sessions.length,
    cleanCount: sessions.filter((session) => session.override_count === 0).length,
    totalFocusMinutes: Math.round(totalFocusMinutes * 100) / 100,
    byHour,
    byDayOfWeek,
    avgDurationMinutes: durations.length
      ? Math.round((durations.reduce((sum, value) => sum + value, 0) / durations.length) * 100) / 100
      : 0,
    fastestWindowHour: fastestWindow?.hour ?? null,
    fastestWindowSampleSize: fastestWindow?.sampleSize ?? 0,
    fastestWindowImprovementPercent: fastestWindow && nextFastestWindow
      ? Math.max(0, Math.round((nextFastestWindow.averageRatio - fastestWindow.averageRatio) * 100))
      : 0,
    hardestSession,
  };
}

function buildBlockingMetrics(
  temptations: TemptationEntry[],
): AnalyticsSnapshot['blocking'] {
  const byHour = emptyHourBuckets();
  const byApp: AnalyticsSnapshot['blocking']['byApp'] = {};

  for (const entry of temptations) {
    const date = new Date(entry.timestamp);
    if (!Number.isNaN(date.getTime())) byHour[date.getHours()] += 1;
    const current = byApp[entry.pkg] ?? { appName: entry.appName || entry.pkg, count: 0 };
    current.count += 1;
    byApp[entry.pkg] = current;
  }

  const topApp = Object.values(byApp).sort((a, b) => b.count - a.count)[0] ?? null;
  const peak = Object.entries(byHour).sort(([, a], [, b]) => b - a)[0];
  return {
    totalAttempts: temptations.length,
    byHour,
    byApp,
    peakHour: peak && peak[1] > 0 ? Number(peak[0]) : null,
    topApp,
    topAppShare: topApp && temptations.length > 0 ? topApp.count / temptations.length : null,
  };
}

function getExpectedTrendWeekStarts(
  range: AnalyticsRange,
  count: number,
  weeklyRates: WeeklyCompletionRateRow[],
): string[] {
  const latest = range.trendWeekAnchor ?? range.end.startOf('week');
  return Array.from({ length: count }, (_, index) =>
    latest.subtract(count - index - 1, 'week').format('YYYY-MM-DD'),
  );
}

function buildTrendMetrics(
  weeklyRates: WeeklyCompletionRateRow[],
  blockingAttempts: number,
  blockingAttemptsPrev: number | null,
  range: AnalyticsRange,
  expectedWeekCount: number,
): NonNullable<AnalyticsSnapshot['trends']> {
  const ratesByWeek = new Map(weeklyRates.map((row) => [row.week_start, row]));
  const weekByWeek = getExpectedTrendWeekStarts(range, expectedWeekCount, weeklyRates).map((weekStart) => {
    const row = ratesByWeek.get(weekStart);
    return {
      weekStart,
      completionRate: row && row.total > 0 ? row.completed / row.total : 0,
      hasData: Boolean(row && row.total > 0),
    };
  });
  const weeksWithData = weekByWeek.filter((week) => week.hasData).length;
  const current = weekByWeek.at(-1)?.completionRate ?? 0;
  const previous = weekByWeek.at(-2)?.completionRate ?? null;
  return {
    completionRatePrev: previous,
    completionRateCurr: current,
    blockingAttemptsPrev,
    blockingAttemptsCurr: blockingAttempts,
    weekByWeek,
    weeksWithData,
  };
}

function getPreviousAnalyticsRange(range: AnalyticsRange): AnalyticsRange {
  const durationMs = range.end.valueOf() - range.start.valueOf() + 1;
  const end = range.start.subtract(1, 'millisecond');
  return {
    start: end.subtract(durationMs - 1, 'millisecond'),
    end,
  };
}

function getInclusiveLocalDayCount(range: AnalyticsRange): number {
  return Math.max(
    1,
    range.end.startOf('day').diff(range.start.startOf('day'), 'day') + 1,
  );
}

function getPhoneUsagePeriod(
  hour: number | null,
): NonNullable<AnalyticsSnapshot['phoneUsage']>['peakPeriod'] {
  if (hour === null) return null;
  if (hour >= 5 && hour <= 11) return 'morning';
  if (hour >= 12 && hour <= 16) return 'afternoon';
  if (hour >= 17 && hour <= 20) return 'evening';
  return 'night';
}

function buildPhoneUsageMetrics(
  usageSummary: UsageSummary | null | undefined,
  usageHourly: UsageHourlySummary | null | undefined,
  range: AnalyticsRange,
): AnalyticsSnapshot['phoneUsage'] {
  if (!usageHourly || !Array.isArray(usageHourly.foregroundMillisecondsByHour)) {
    return undefined;
  }

  const dayCount = getInclusiveLocalDayCount(range);
  const byHour = emptyHourBuckets();
  for (let hour = 0; hour < 24; hour += 1) {
    const milliseconds = usageHourly.foregroundMillisecondsByHour[hour] ?? 0;
    if (Number.isFinite(milliseconds) && milliseconds > 0) {
      byHour[hour] = Math.round((milliseconds / 60_000 / dayCount) * 100) / 100;
    }
  }

  const peak = Object.entries(byHour).sort(([, a], [, b]) => b - a)[0];
  return {
    byHour,
    peakHour: peak && peak[1] > 0 ? Number(peak[0]) : null,
    peakPeriod: getPhoneUsagePeriod(peak && peak[1] > 0 ? Number(peak[0]) : null),
    heaviestApp: usageSummary?.apps?.[0]
      ? {
          appName: usageSummary.apps[0].appName || usageSummary.apps[0].packageName,
          minutes: usageSummary.apps[0].foregroundMinutes,
        }
      : null,
  };
}

export function createAnalyticsSnapshot(
  window: AnalyticsWindow,
  range: AnalyticsRange,
  source: AnalyticsSourceData,
  generatedAt = new Date().toISOString(),
): AnalyticsSnapshot {
  return {
    generatedAt,
    window,
    range: { startISO: range.start.toISOString(), endISO: range.end.toISOString() },
    tasks: buildTaskMetrics(source.tasks, source.estimationErrors, source.tasksByHour, source.tasksThisWeek),
    sessions: buildSessionMetrics(source.sessions, source.estimationErrors),
    blocking: buildBlockingMetrics(source.temptations),
    trends: buildTrendMetrics(
      source.weeklyRates,
      source.temptations.length,
      source.previousTemptations ? source.previousTemptations.length : null,
      range,
      window === 'three_months' ? 12 : 2,
    ),
    phoneUsage: window === 'three_months'
      ? buildPhoneUsageMetrics(source.usageSummary, source.usageHourly, range)
      : undefined,
    sourceHealth: source.health,
  };
}

async function readSource<T>(
  read: () => Promise<T>,
  fallback: T,
): Promise<{ value: T; state: AnalyticsSourceState }> {
  try {
    const value = await read();
    return { value, state: 'loaded' };
  } catch {
    return { value: fallback, state: 'failed' };
  }
}

export async function buildAnalyticsSnapshot(
  window: AnalyticsWindow,
  options: { now?: Dayjs; weekStartDay?: number; usageStatsPermission?: boolean } = {},
): Promise<AnalyticsSnapshot> {
  const range = getAnalyticsRange(window, options.now, options.weekStartDay ?? 0);
  const startISO = range.start.toISOString();
  const endISO = range.end.toISOString();
  const previousRange = getPreviousAnalyticsRange(range);
  const previousStartMs = previousRange.start.valueOf();
  const previousEndMs = previousRange.end.valueOf();
  const expectedWeekCount = window === 'three_months' ? 12 : 2;
  const weekForSkipComparison = window === 'yesterday'
    ? getAnalyticsRange('week', options.now, options.weekStartDay ?? 0)
    : null;
  const canReadUsageStats =
    window === 'three_months' &&
    options.usageStatsPermission === true;
  const usageReads: [
    { value: UsageSummary | null; state: AnalyticsSourceState },
    { value: UsageHourlySummary | null; state: AnalyticsSourceState },
  ] = canReadUsageStats
    ? await Promise.all([
        isUsageSummaryAvailable
          ? readSource(() => UsageStatsModule.getUsageSummary(range.start.valueOf(), range.end.valueOf()), null)
          : Promise.resolve({ value: null, state: 'unavailable' as AnalyticsSourceState }),
        isUsageHourlySummaryAvailable
          ? readSource(() => UsageStatsModule.getHourlyUsageSummary(range.start.valueOf(), range.end.valueOf()), null)
          : Promise.resolve({ value: null, state: 'unavailable' as AnalyticsSourceState }),
      ])
    : [
        { value: null, state: 'unavailable' as AnalyticsSourceState },
        { value: null, state: 'unavailable' as AnalyticsSourceState },
      ];

  const [
    tasksResult,
    tasksThisWeekResult,
    sessionsResult,
    estimationErrorsResult,
    tasksByHourResult,
    weeklyRatesResult,
    temptationsResult,
  ] = await Promise.all([
    readSource(() => dbGetTasksInDateRange(startISO, endISO), []),
    weekForSkipComparison
      ? readSource(
          () => dbGetTasksInDateRange(
            weekForSkipComparison.start.toISOString(),
            weekForSkipComparison.end.toISOString(),
          ),
          [],
        )
      : Promise.resolve({ value: undefined, state: 'unavailable' as AnalyticsSourceState }),
    readSource(() => dbGetSessionsWithOverrideCount(startISO, endISO), []),
    readSource(() => dbGetEstimationErrors(startISO, endISO), []),
    readSource(() => dbGetTasksByHourOfDay(startISO, endISO), []),
    readSource(() => dbGetWeeklyCompletionRates(expectedWeekCount), []),
    readSource(() => GreyoutModule.getTemptationLog(), []),
  ]);
  const allTemptations = temptationsResult.value;
  const inRangeTemptations = allTemptations.filter(
    (entry) => entry.timestamp >= range.start.valueOf() && entry.timestamp <= range.end.valueOf(),
  );
  const previousTemptations = allTemptations.filter(
    (entry) => entry.timestamp >= previousStartMs && entry.timestamp <= previousEndMs,
  );
  return createAnalyticsSnapshot(
    window,
    range,
    {
      tasks: tasksResult.value,
      tasksThisWeek: tasksThisWeekResult.value,
      sessions: sessionsResult.value,
      estimationErrors: estimationErrorsResult.value,
      tasksByHour: tasksByHourResult.value,
      weeklyRates: weeklyRatesResult.value,
      temptations: inRangeTemptations,
      previousTemptations,
      usageSummary: usageReads[0].value,
      usageHourly: usageReads[1].value,
      health: {
        tasks: tasksResult.state,
        sessions: sessionsResult.state,
        estimationErrors: estimationErrorsResult.state,
        tasksByHour: tasksByHourResult.state,
        weeklyRates: weeklyRatesResult.state,
        temptations: temptationsResult.state,
        usageSummary: usageReads[0].state,
        usageHourly: usageReads[1].state,
      },
    },
  );
}