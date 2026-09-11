import type { InsightRule } from '../InsightEngine';
import { hourLabel } from '../InsightTemplates';

export const threeMonthRules: readonly InsightRule[] = [
  {
    id: 'THREE_MONTH_INSUFFICIENT_DATA',
    category: 'nothing_to_report',
    condition: (snapshot) => (snapshot.trends?.weekByWeek.length ?? 0) < 4,
    priority: () => 100,
    render: (snapshot) => ({
      id: 'THREE_MONTH_INSUFFICIENT_DATA',
      category: 'nothing_to_report',
      priority: 100,
      headline: 'Not enough history yet',
      body: `There are ${snapshot.trends?.weekByWeek.length ?? 0} weeks of data. Come back after more of your routine has accumulated.`,
      sentiment: 'neutral',
    }),
  },
  {
    id: 'THREE_MONTH_SCHEDULING_HONESTY',
    category: 'pattern',
    condition: (snapshot) =>
      Object.values(snapshot.tasks.byDayOfWeek).some(
        (bucket) => bucket.total > 5 && bucket.completed / bucket.total < 0.4,
      ),
    priority: () => 95,
    render: (snapshot) => {
      const [day, bucket] = Object.entries(snapshot.tasks.byDayOfWeek)
        .filter(([, value]) => value.total > 5)
        .sort(([, a], [, b]) => a.completed / a.total - b.completed / b.total)[0] ?? ['0', { total: 0, completed: 0 }];
      const dayName = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'][Number(day)] ?? 'That day';
      return {
        id: 'THREE_MONTH_SCHEDULING_HONESTY',
        category: 'pattern',
        priority: 95,
        headline: `${dayName} is not matching the schedule`,
        body: `You completed ${Math.round((bucket.completed / Math.max(1, bucket.total)) * 100)}% of tasks scheduled that day.`,
        sentiment: 'warning',
      };
    },
  },
  {
    id: 'THREE_MONTH_IMPROVING',
    category: 'trend',
    condition: (snapshot) => {
      const rates = snapshot.trends?.weekByWeek.map((week) => week.completionRate) ?? [];
      return rates.length >= 4 && rates.at(-1)! - rates[0] >= 0.1;
    },
    priority: () => 85,
    render: (snapshot) => {
      const rates = snapshot.trends?.weekByWeek.map((week) => week.completionRate) ?? [];
      return {
        id: 'THREE_MONTH_IMPROVING',
        category: 'trend',
        priority: 85,
        headline: 'You are getting better at this',
        body: `Completion is up ${Math.round((rates.at(-1)! - rates[0]) * 100)} points from where you started.`,
        sentiment: 'positive',
      };
    },
  },
  {
    id: 'THREE_MONTH_FIRST_TASK_WINDOW',
    category: 'positive',
    condition: (snapshot) => snapshot.tasks.firstTaskHour !== null && snapshot.tasks.total >= 5,
    priority: () => 55,
    render: (snapshot) => ({
      id: 'THREE_MONTH_FIRST_TASK_WINDOW',
      category: 'positive',
      priority: 55,
      headline: 'Your routine has a starting point',
      body: `Your earliest scheduled work typically begins around ${hourLabel(snapshot.tasks.firstTaskHour)}.`,
      sentiment: 'neutral',
    }),
  },
];