import type { InsightRule } from '../InsightEngine';
import { hourLabel } from '../InsightTemplates';

export const yesterdayRules: readonly InsightRule[] = [
  {
    id: 'YESTERDAY_PERFECT_DAY',
    category: 'positive',
    condition: (snapshot) =>
      snapshot.blocking.totalAttempts === 0 &&
      snapshot.tasks.total > 0 &&
      snapshot.tasks.completed === snapshot.tasks.total,
    priority: () => 90,
    render: (snapshot) => ({
      id: 'YESTERDAY_PERFECT_DAY',
      category: 'positive',
      priority: 90,
      headline: 'Clean day',
      body: 'You finished everything and never reached for a blocked app.',
      sentiment: 'positive',
    }),
  },
  {
    id: 'YESTERDAY_CLEAN_NO_BLOCKS',
    category: 'positive',
    condition: (snapshot) =>
      snapshot.blocking.totalAttempts === 0 &&
      snapshot.tasks.total > 0 &&
      snapshot.tasks.completed < snapshot.tasks.total,
    priority: () => 60,
    render: () => ({
      id: 'YESTERDAY_CLEAN_NO_BLOCKS',
      category: 'positive',
      priority: 60,
      headline: 'The distraction side was clean',
      body: 'You never reached for a blocked app yesterday.',
      sentiment: 'positive',
    }),
  },
  {
    id: 'YESTERDAY_PEAK_BLOCK_HOUR',
    category: 'resistance',
    condition: (snapshot) => snapshot.blocking.totalAttempts > 0,
    priority: () => 75,
    render: (snapshot) => ({
      id: 'YESTERDAY_PEAK_BLOCK_HOUR',
      category: 'resistance',
      priority: 75,
      headline: `${snapshot.blocking.totalAttempts} blocked-app attempts`,
      body: `${hourLabel(snapshot.blocking.peakHour)} was the hardest hour, with ${snapshot.blocking.peakHour === null ? 0 : snapshot.blocking.byHour[snapshot.blocking.peakHour]} of them.`,
      sentiment: snapshot.blocking.totalAttempts >= 10 ? 'warning' : 'neutral',
    }),
  },
  {
    id: 'YESTERDAY_NOTHING_NOTABLE',
    category: 'nothing_to_report',
    condition: () => true,
    priority: () => 10,
    render: () => ({
      id: 'YESTERDAY_NOTHING_NOTABLE',
      category: 'nothing_to_report',
      priority: 10,
      headline: 'Nothing unusual',
      body: 'Ordinary day. You showed up, you worked, nothing unusual happened.',
      sentiment: 'neutral',
    }),
  },
];