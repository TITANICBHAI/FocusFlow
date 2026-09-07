import dayjs from 'dayjs';
import type { Task } from '@/data/types';

export interface Insight {
  id: string;
  text: string;
  significance: number;
}

export interface AnalysisResult {
  headline: string;
  insights: Insight[];
}

function completionRate(tasks: Task[]): number {
  if (tasks.length === 0) return 0;
  return tasks.filter((task) => task.status === 'completed').length / tasks.length;
}

function detectTimeOfDaySplit(tasks: Task[]): Insight | null {
  const before = tasks.filter((task) => dayjs(task.startTime).hour() < 14);
  const after = tasks.filter((task) => dayjs(task.startTime).hour() >= 14);
  if (before.length < 2 || after.length < 2) return null;

  const beforeRate = completionRate(before);
  const afterRate = completionRate(after);
  const delta = Math.abs(beforeRate - afterRate);
  if (delta < 0.25) return null;

  const stronger = beforeRate > afterRate ? 'morning' : 'afternoon';
  const weaker = beforeRate > afterRate ? 'afternoon' : 'morning';
  return {
    id: 'time-of-day-split',
    text: `Completion held strong in the ${stronger} (${Math.round(Math.max(beforeRate, afterRate) * 100)}%) but dropped in the ${weaker} (${Math.round(Math.min(beforeRate, afterRate) * 100)}%).`,
    significance: Math.min(delta, 0.9),
  };
}

function detectRecurringSkip(tasks: Task[]): Insight | null {
  const counts = new Map<string, number>();
  for (const task of tasks) {
    if (task.status === 'skipped') counts.set(task.title, (counts.get(task.title) ?? 0) + 1);
  }
  const top = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
  if (!top || top[1] < 3) return null;
  return {
    id: 'recurring-skip',
    text: `"${top[0]}" has been skipped ${top[1]} times recently — worth reconsidering its time slot.`,
    significance: 0.6 + Math.min(top[1] * 0.05, 0.25),
  };
}

function detectBaselineComparison(dayTasks: Task[], baselineTasks: Task[]): Insight | null {
  const focusToday = dayTasks
    .filter((task) => task.status === 'completed' && task.focusMode)
    .reduce((sum, task) => sum + task.durationMinutes, 0);
  const byDate = new Map<string, number>();
  for (const task of baselineTasks) {
    if (task.status !== 'completed' || !task.focusMode) continue;
    const date = dayjs(task.startTime).format('YYYY-MM-DD');
    byDate.set(date, (byDate.get(date) ?? 0) + task.durationMinutes);
  }
  if (byDate.size < 5) return null;
  const average = [...byDate.values()].reduce((sum, value) => sum + value, 0) / byDate.size;
  if (average === 0) return null;

  const difference = (focusToday - average) / average;
  if (Math.abs(difference) < 0.15) return null;
  return {
    id: 'baseline-comparison',
    text: `${focusToday} minutes of focus time today — ${Math.round(Math.abs(difference) * 100)}% ${difference > 0 ? 'more' : 'less'} than your recent daily average.`,
    significance: Math.min(Math.abs(difference), 0.85),
  };
}

function detectRecoveryArc(tasks: Task[]): Insight | null {
  const morning = tasks.filter((task) => dayjs(task.startTime).hour() < 12);
  const rest = tasks.filter((task) => dayjs(task.startTime).hour() >= 12);
  if (morning.length < 2 || rest.length < 2) return null;
  const morningRate = completionRate(morning);
  const restRate = completionRate(rest);

  if (morningRate < 0.5 && restRate > 0.8) {
    return {
      id: 'recovery-arc',
      text: `A slow start didn't define the day — you closed out ${Math.round(restRate * 100)}% of what was left after midday.`,
      significance: 0.7,
    };
  }
  if (morningRate > 0.8 && restRate < 0.5) {
    return {
      id: 'fade-arc',
      text: `Strong start, but momentum faded — only ${Math.round(restRate * 100)}% completed after midday versus ${Math.round(morningRate * 100)}% before.`,
      significance: 0.7,
    };
  }
  return null;
}

function groupByDate(tasks: Task[]): Map<string, Task[]> {
  const grouped = new Map<string, Task[]>();
  for (const task of tasks) {
    const date = dayjs(task.startTime).format('YYYY-MM-DD');
    const existing = grouped.get(date) ?? [];
    existing.push(task);
    grouped.set(date, existing);
  }
  return grouped;
}

function detectBestWeakestDay(tasks: Task[]): Insight | null {
  const byDate = groupByDate(tasks);
  if (byDate.size < 3) return null;
  const rates = [...byDate.entries()]
    .map(([date, dayTasks]) => ({ date, rate: completionRate(dayTasks) }))
    .sort((a, b) => b.rate - a.rate);
  const best = rates[0];
  const weakest = rates[rates.length - 1];
  if (best.rate - weakest.rate < 0.3) return null;
  return {
    id: 'best-weakest-day',
    text: `${dayjs(best.date).format('dddd')} was the strongest day (${Math.round(best.rate * 100)}%); ${dayjs(weakest.date).format('dddd')} was the weakest (${Math.round(weakest.rate * 100)}%).`,
    significance: Math.min(best.rate - weakest.rate, 0.85),
  };
}

function detectWeekOverWeekTrend(weekTasks: Task[], previousTasks: Task[]): Insight | null {
  if (previousTasks.length < 3) return null;
  const delta = completionRate(weekTasks) - completionRate(previousTasks);
  if (Math.abs(delta) < 0.1) return null;
  return {
    id: 'week-trend',
    text: `Completion is ${delta > 0 ? 'up' : 'down'} ${Math.round(Math.abs(delta) * 100)} points versus last week.`,
    significance: Math.min(Math.abs(delta), 0.8),
  };
}

function buildHeadline(
  rate: number,
  topInsight: Insight | null,
  labels: [string, string, string, string],
): string {
  const label = rate >= 0.85 ? labels[0] : rate >= 0.6 ? labels[1] : rate >= 0.4 ? labels[2] : labels[3];
  if (!topInsight) return `${label}.`;
  const clause = topInsight.text.split(/[;—]/)[0].replace(/\.$/, '').toLowerCase();
  return `${label} — ${clause}.`;
}

export function computeDailyAnalysis(dayTasks: Task[], baselineTasks: Task[]): AnalysisResult {
  const insights = [
    detectTimeOfDaySplit(dayTasks),
    detectBaselineComparison(dayTasks, baselineTasks),
    detectRecoveryArc(dayTasks),
    detectRecurringSkip(baselineTasks),
  ]
    .filter((insight): insight is Insight => insight !== null)
    .sort((a, b) => b.significance - a.significance);
  return {
    headline: buildHeadline(completionRate(dayTasks), insights[0] ?? null, [
      'Excellent day',
      'Solid day',
      'Mixed day',
      'Rough day',
    ]),
    insights: insights.slice(0, 3),
  };
}

export function computeWeeklyAnalysis(
  weekTasks: Task[],
  previousWeekTasks: Task[],
): AnalysisResult {
  const insights = [
    detectBestWeakestDay(weekTasks),
    detectWeekOverWeekTrend(weekTasks, previousWeekTasks),
    detectTimeOfDaySplit(weekTasks),
    detectRecurringSkip(weekTasks),
  ]
    .filter((insight): insight is Insight => insight !== null)
    .sort((a, b) => b.significance - a.significance);
  return {
    headline: buildHeadline(completionRate(weekTasks), insights[0] ?? null, [
      'Strong week',
      'Solid week',
      'Uneven week',
      'Tough week',
    ]),
    insights: insights.slice(0, 4),
  };
}