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

export type AnalyticsWindow = 'yesterday' | 'week' | 'three_months';

export interface AnalyticsSnapshot {
  generatedAt: string;
  window: AnalyticsWindow;
  range: { startISO: string; endISO: string };
  tasks: {
    total: number;
    completed: number;
    skipped: number;
    missed: number;
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
    avgDurationMinutes: number;
    fastestWindowHour: number | null;
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
    weekByWeek: { weekStart: string; completionRate: number }[];
  };
}

export interface AnalyticsSourceData {
  tasks: Task[];
  sessions: SessionOverrideCountRow[];
  estimationErrors: EstimationErrorRow[];
  tasksByHour: TasksByHourRow[];
  weeklyRates: WeeklyCompletionRateRow[];
  temptations: TemptationEntry[];
}

export interface AnalyticsRange {
  start: Dayjs;
  end: Dayjs;
}

export function getAnalyticsRange(
  window: AnalyticsWindow,
  now: Dayjs = dayjs(),
  weekStartDay = 0,
): AnalyticsRange {
  if (window === 'yesterday') {
    const day = now.subtract(1, 'day');
    return { start: day.startOf('day'), end: day.endOf('day') };
  }
  if (window === 'three_months') {
    return { start: now.subtract(89, 'day').startOf('day'), end: now.endOf('day') };
  }
  const offset = (now.day() - weekStartDay + 7) % 7;
  const start = now.subtract(offset, 'day').startOf('day');
  return { start, end: start.add(6, 'day').endOf('day') };
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
): AnalyticsSnapshot['tasks'] {
  const byHour = emptyTaskHourBuckets();
  const byDayOfWeek = emptyDayBuckets();
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

  return {
    total: tasks.length,
    completed: tasks.filter((task) => task.status === 'completed').length,
    skipped: tasks.filter((task) => task.status === 'skipped').length,
    missed: tasks.filter((task) => task.status === 'overdue').length,
    byHour,
    byDayOfWeek,
    estimationErrorMinutes: estimationErrors
      .map((row) => row.actual_minutes - row.planned_minutes)
      .filter(Number.isFinite),
    firstTaskHour,
  };
}

function buildSessionMetrics(
  sessions: SessionOverrideCountRow[],
): AnalyticsSnapshot['sessions'] {
  const byHour = emptyHourBuckets();
  let totalFocusMinutes = 0;
  const durations: number[] = [];

  for (const session of sessions) {
    const start = new Date(session.started_at);
    if (Number.isNaN(start.getTime())) continue;
    byHour[start.getHours()] += 1;
    const end = session.ended_at ? new Date(session.ended_at) : new Date();
    const duration = Math.max(0, (end.getTime() - start.getTime()) / 60000);
    totalFocusMinutes += duration;
    durations.push(duration);
  }

  return {
    total: sessions.length,
    cleanCount: sessions.filter((session) => session.override_count === 0).length,
    totalFocusMinutes: Math.round(totalFocusMinutes * 100) / 100,
    byHour,
    avgDurationMinutes: durations.length
      ? Math.round((durations.reduce((sum, value) => sum + value, 0) / durations.length) * 100) / 100
      : 0,
    // Calculating this requires session-to-task estimation rows grouped by
    // start hour; keep it null until that source contract is extended.
    fastestWindowHour: null,
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

function buildTrendMetrics(
  weeklyRates: WeeklyCompletionRateRow[],
  blockingAttempts: number,
): NonNullable<AnalyticsSnapshot['trends']> {
  const weekByWeek = weeklyRates.map((row) => ({
    weekStart: row.week_start,
    completionRate: row.total > 0 ? row.completed / row.total : 0,
  }));
  const current = weekByWeek.at(-1)?.completionRate ?? 0;
  const previous = weekByWeek.at(-2)?.completionRate ?? null;
  return {
    completionRatePrev: previous,
    completionRateCurr: current,
    blockingAttemptsPrev: null,
    blockingAttemptsCurr: blockingAttempts,
    weekByWeek,
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
    tasks: buildTaskMetrics(source.tasks, source.estimationErrors, source.tasksByHour),
    sessions: buildSessionMetrics(source.sessions),
    blocking: buildBlockingMetrics(source.temptations),
    trends: buildTrendMetrics(source.weeklyRates, source.temptations.length),
  };
}

export async function buildAnalyticsSnapshot(
  window: AnalyticsWindow,
  options: { now?: Dayjs; weekStartDay?: number } = {},
): Promise<AnalyticsSnapshot> {
  const range = getAnalyticsRange(window, options.now, options.weekStartDay ?? 0);
  const startISO = range.start.toISOString();
  const endISO = range.end.toISOString();
  const [tasks, sessions, estimationErrors, tasksByHour, weeklyRates, temptations] = await Promise.all([
    dbGetTasksInDateRange(startISO, endISO),
    dbGetSessionsWithOverrideCount(startISO, endISO),
    dbGetEstimationErrors(startISO, endISO),
    dbGetTasksByHourOfDay(startISO, endISO),
    dbGetWeeklyCompletionRates(window === 'three_months' ? 12 : 2),
    GreyoutModule.getTemptationLog(),
  ]);
  const inRangeTemptations = temptations.filter(
    (entry) => entry.timestamp >= range.start.valueOf() && entry.timestamp <= range.end.valueOf(),
  );
  return createAnalyticsSnapshot(
    window,
    range,
    { tasks, sessions, estimationErrors, tasksByHour, weeklyRates, temptations: inRangeTemptations },
  );
}