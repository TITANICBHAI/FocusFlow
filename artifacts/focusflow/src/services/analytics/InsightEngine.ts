import type { AnalyticsSnapshot, AnalyticsWindow } from './AnalyticsProcessor';
import {
  dbGetRecentWeeklyInsightIds,
  dbRecordWeeklyInsight,
} from '@/data/database';
import { threeMonthRules } from './InsightRules/ThreeMonthRules';
import { weeklyRules } from './InsightRules/WeeklyRules';
import { yesterdayRules } from './InsightRules/YesterdayRules';
import { weekSeed } from './InsightTemplates';

export type InsightCategory =
  | 'task'
  | 'session'
  | 'resistance'
  | 'pattern'
  | 'trend'
  | 'positive'
  | 'nothing_to_report';

export interface InsightCard {
  id: string;
  category: InsightCategory;
  priority: number;
  headline: string;
  body: string;
  sentiment: 'positive' | 'neutral' | 'warning';
}

export interface InsightRule {
  id: string;
  category: InsightCategory;
  condition: (snapshot: AnalyticsSnapshot) => boolean;
  priority: (snapshot: AnalyticsSnapshot) => number;
  render: (snapshot: AnalyticsSnapshot, seed: number) => InsightCard;
}

const rulesByWindow: Record<AnalyticsWindow, readonly InsightRule[]> = {
  yesterday: yesterdayRules,
  week: weeklyRules,
  three_months: threeMonthRules,
};

export function buildInsights(
  snapshot: AnalyticsSnapshot,
  limit = snapshot.window === 'three_months' ? 5 : 4,
): InsightCard[] {
  const seed = weekSeed(snapshot.generatedAt);
  const candidates = rulesByWindow[snapshot.window]
    .filter((rule) => rule.condition(snapshot))
    .map((rule) => ({ card: rule.render(snapshot, seed), priority: rule.priority(snapshot) }))
    .sort((a, b) => b.priority - a.priority);
  return candidates.slice(0, Math.max(1, limit)).map(({ card }) => card);
}

export function selectWeeklyStandout(
  snapshot: AnalyticsSnapshot,
  previousInsightIds: readonly string[],
): InsightCard {
  const previous = new Set(previousInsightIds);
  const candidates = buildInsights(snapshot, 8)
    .filter((card) => card.category !== 'nothing_to_report');
  return candidates.find((card) => !previous.has(card.id)) ?? {
    id: 'WEEKLY_NOTHING_UNUSUAL',
    category: 'nothing_to_report',
    priority: 1,
    headline: 'Nothing unusual this week',
    body: 'No single signal stood out enough to repeat. Keep building the routine.',
    sentiment: 'neutral',
  };
}

export async function syncWeeklyStandout(snapshot: AnalyticsSnapshot): Promise<InsightCard> {
  if (snapshot.window !== 'week') {
    throw new Error('Weekly standout requires the week analytics window');
  }
  const previousIds = await dbGetRecentWeeklyInsightIds();
  const standout = selectWeeklyStandout(snapshot, previousIds);
  await dbRecordWeeklyInsight(snapshot.range.startISO.slice(0, 10), standout.id);
  return standout;
}