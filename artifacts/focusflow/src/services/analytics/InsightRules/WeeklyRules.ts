import type { InsightRule } from '../InsightEngine';
import { hourLabel, hourWindowLabel, pickDeterministicVariant } from '../InsightTemplates';

export const weeklyRules: readonly InsightRule[] = [
  {
    id: 'WEEKLY_VULNERABLE_WINDOW',
    category: 'resistance',
    condition: (snapshot) => snapshot.blocking.totalAttempts > 0 && snapshot.blocking.peakHour !== null,
    priority: (snapshot) => {
      const hour = snapshot.blocking.peakHour;
      return hour === null ? 0 : snapshot.blocking.byHour[hour] > 8 ? 90 : 85;
    },
    render: (snapshot, seed) => {
      const hour = snapshot.blocking.peakHour;
      const count = hour === null ? 0 : snapshot.blocking.byHour[hour];
      return {
        id: 'WEEKLY_VULNERABLE_WINDOW',
        category: 'resistance',
        priority: 85,
        headline: `${hourLabel(hour)} is your weakest hour`,
        body: pickDeterministicVariant([
          `${count} of your ${snapshot.blocking.totalAttempts} blocked-app attempts happened then.`,
          `Between ${hourWindowLabel(hour)}, you reached for a blocked app ${count} times.`,
        ], seed),
        sentiment: count > 8 ? 'warning' : 'neutral',
      };
    },
  },
  {
    id: 'WEEKLY_ONE_APP',
    category: 'pattern',
    condition: (snapshot) => (snapshot.blocking.topAppShare ?? 0) > 0.5,
    priority: () => 88,
    render: (snapshot) => ({
      id: 'WEEKLY_ONE_APP',
      category: 'pattern',
      priority: 88,
      headline: `${snapshot.blocking.topApp?.appName ?? 'One app'} is the habit`,
      body: `${Math.round((snapshot.blocking.topAppShare ?? 0) * 100)}% of your blocked-app attempts came from ${snapshot.blocking.topApp?.appName ?? 'one app'}.`,
      sentiment: 'warning',
    }),
  },
  {
    id: 'WEEKLY_ESTIMATION',
    category: 'task',
    condition: (snapshot) => snapshot.tasks.estimationErrorMinutes.length > 0,
    priority: (snapshot) => {
      const errors = snapshot.tasks.estimationErrorMinutes;
      const average = errors.reduce((sum, value) => sum + value, 0) / errors.length;
      return average > 20 || average < -15 ? 72 : 65;
    },
    render: (snapshot) => {
      const errors = snapshot.tasks.estimationErrorMinutes;
      const average = errors.reduce((sum, value) => sum + value, 0) / errors.length;
      const rounded = Math.round(Math.abs(average));
      const under = average > 20;
      return {
        id: 'WEEKLY_ESTIMATION',
        category: 'task',
        priority: under ? 72 : 65,
        headline: under ? 'You are underestimating' : 'Your estimates were solid',
        body: under
          ? `Tasks ran ${rounded} minutes over schedule on average this week.`
          : `You were off by ${rounded} minutes on average this week.`,
        sentiment: under ? 'warning' : 'positive',
      };
    },
  },
  {
    id: 'WEEKLY_SHOWED_UP',
    category: 'positive',
    condition: () => true,
    priority: () => 40,
    render: (snapshot) => {
      const days = Object.values(snapshot.tasks.byDayOfWeek).filter((bucket) => bucket.total > 0).length;
      return {
        id: 'WEEKLY_SHOWED_UP',
        category: 'positive',
        priority: 40,
        headline: `You showed up ${days} of 7 days`,
        body: days === 7 ? 'Every day this week. That is the whole job.' : 'Presence matters more than a perfect run.',
        sentiment: days >= 5 ? 'positive' : days < 3 ? 'warning' : 'neutral',
      };
    },
  },
  {
    id: 'WEEKLY_FLAT',
    category: 'nothing_to_report',
    condition: (snapshot) => {
      const trend = snapshot.trends;
      return Boolean(
        trend &&
        trend.completionRatePrev !== null &&
        Math.abs(trend.completionRateCurr - trend.completionRatePrev) < 0.05,
      );
    },
    priority: () => 35,
    render: () => ({
      id: 'WEEKLY_FLAT',
      category: 'nothing_to_report',
      priority: 35,
      headline: 'Consistent with last week',
      body: 'Not better, not worse. The routine is holding steady.',
      sentiment: 'neutral',
    }),
  },
];