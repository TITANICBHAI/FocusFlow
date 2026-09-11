import type { InsightRule } from '../InsightEngine';
import {
  hourLabel,
  renderInsightVariant,
  taskResultLine,
} from '../InsightTemplates';

export const yesterdayRules: readonly InsightRule[] = [
  {
    id: 'YESTERDAY_PERFECT_DAY',
    category: 'positive',
    condition: (snapshot) =>
      snapshot.blocking.totalAttempts === 0 &&
      snapshot.tasks.total > 0 &&
      snapshot.tasks.completed === snapshot.tasks.total,
    priority: () => 90,
    render: (snapshot, seed) => ({
      id: 'YESTERDAY_PERFECT_DAY',
      category: 'positive',
      priority: 90,
      headline: 'Clean day',
      body: renderInsightVariant('YESTERDAY_PERFECT_DAY', seed),
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
    render: (snapshot, seed) => ({
      id: 'YESTERDAY_CLEAN_NO_BLOCKS',
      category: 'positive',
      priority: 60,
      headline: 'The distraction side was clean',
      body: renderInsightVariant('YESTERDAY_CLEAN_NO_BLOCKS', seed),
      sentiment: 'positive',
    }),
  },
  {
    id: 'YESTERDAY_PEAK_BLOCK_HOUR',
    category: 'resistance',
    condition: (snapshot) => snapshot.blocking.totalAttempts > 0,
    priority: () => 75,
    render: (snapshot, seed) => ({
      id: 'YESTERDAY_PEAK_BLOCK_HOUR',
      category: 'resistance',
      priority: 75,
      headline: `${snapshot.blocking.totalAttempts} blocked-app attempts`,
      body: renderInsightVariant('YESTERDAY_PEAK_BLOCK_HOUR', seed, {
        count: snapshot.blocking.totalAttempts,
        peak_hour_label: hourLabel(snapshot.blocking.peakHour),
        peak_count: snapshot.blocking.peakHour === null ? 0 : snapshot.blocking.byHour[snapshot.blocking.peakHour],
      }),
      sentiment: snapshot.blocking.totalAttempts >= 10 ? 'warning' : 'neutral',
    }),
  },
  {
    id: 'YESTERDAY_SINGLE_HARD_SESSION',
    category: 'session',
    condition: (snapshot) => (snapshot.sessions.hardestSession?.attempts ?? 0) > 5,
    priority: () => 80,
    render: (snapshot, seed) => ({
      id: 'YESTERDAY_SINGLE_HARD_SESSION',
      category: 'session',
      priority: 80,
      headline: 'A hard session',
      body: renderInsightVariant('YESTERDAY_SINGLE_HARD_SESSION', seed, {
        session_time: hourLabel(snapshot.sessions.hardestSession?.hour ?? null),
        count: snapshot.sessions.hardestSession?.attempts ?? 0,
      }),
      sentiment: 'neutral',
    }),
  },
  {
    id: 'YESTERDAY_SECOND_SKIP_THIS_WEEK',
    category: 'task',
    condition: (snapshot) => (snapshot.tasks.skippedThisWeek ?? snapshot.tasks.skipped) > 1,
    priority: () => 65,
    render: (snapshot, seed) => ({
      id: 'YESTERDAY_SECOND_SKIP_THIS_WEEK',
      category: 'task',
      priority: 65,
      headline: 'A pattern worth noticing',
      body: renderInsightVariant('YESTERDAY_SECOND_SKIP_THIS_WEEK', seed, {
        count: snapshot.tasks.skippedThisWeek ?? snapshot.tasks.skipped,
      }),
      sentiment: 'neutral',
    }),
  },
  {
    id: 'YESTERDAY_TASK_RESULT',
    category: 'task',
    condition: () => true,
    priority: () => 50,
    render: (snapshot) => ({
      id: 'YESTERDAY_TASK_RESULT',
      category: 'task',
      priority: 50,
      headline: "Today's tasks",
      body: snapshot.tasks.resultRows?.length
        ? snapshot.tasks.resultRows.map((row) => taskResultLine(row.title, row.status)).join('\n')
        : 'No tasks were recorded yesterday.',
      sentiment: 'neutral',
    }),
  },
  {
    id: 'YESTERDAY_NOTHING_NOTABLE',
    category: 'nothing_to_report',
    condition: () => true,
    priority: () => 10,
    render: (snapshot, seed) => ({
      id: 'YESTERDAY_NOTHING_NOTABLE',
      category: 'nothing_to_report',
      priority: 10,
      headline: 'Nothing unusual',
      body: renderInsightVariant('YESTERDAY_NOTHING_NOTABLE', seed),
      sentiment: 'neutral',
    }),
  },
];