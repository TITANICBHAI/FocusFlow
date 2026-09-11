import {
  dbGetEarnedAchievementIds,
  dbRecordEarnedAchievements,
  type LifetimeStats,
} from '@/data/database';
import type { AnalyticsSnapshot } from './AnalyticsProcessor';

export interface AchievementDefinition {
  id: string;
  category: 'resistance' | 'honesty' | 'presence' | 'pattern_breaking' | 'hidden';
  title: string;
  description: string;
  icon: string;
  hidden?: boolean;
  condition: (lifetime: LifetimeStats, snapshot: AnalyticsSnapshot) => boolean;
}

export interface AchievementState {
  definitions: AchievementDefinition[];
  earnedIds: string[];
  newlyEarnedIds: string[];
}

export const ACHIEVEMENTS: readonly AchievementDefinition[] = [
  {
    id: 'RESISTANCE_10_CLEAN_SESSIONS',
    category: 'resistance',
    title: 'Stayed with it',
    description: '10 focus sessions without a blocked-app override.',
    icon: 'shield-checkmark-outline',
    condition: (lifetime) => lifetime.cleanSessions >= 10,
  },
  {
    id: 'HONEST_ESTIMATOR',
    category: 'honesty',
    title: 'Honest estimator',
    description: 'Your recent task estimates are usually within 15 minutes.',
    icon: 'resize-outline',
    condition: (_lifetime, snapshot) => {
      const errors = snapshot.tasks.estimationErrorMinutes;
      if (errors.length < 5) return false;
      const averageAbsoluteError = errors.reduce((sum, value) => sum + Math.abs(value), 0) / errors.length;
      return averageAbsoluteError <= 15;
    },
  },
  {
    id: 'PRESENCE_7_DAYS',
    category: 'presence',
    title: 'Showed up',
    description: 'You scheduled work on all seven days of a week.',
    icon: 'calendar-outline',
    condition: (_lifetime, snapshot) =>
      snapshot.window === 'week' &&
      Object.values(snapshot.tasks.byDayOfWeek).filter((bucket) => bucket.total > 0).length === 7,
  },
  {
    id: 'PATTERN_BREAKER',
    category: 'pattern_breaking',
    title: 'Pattern breaker',
    description: 'No single blocked app accounts for most of your recent attempts.',
    icon: 'git-compare-outline',
    condition: (_lifetime, snapshot) =>
      snapshot.blocking.totalAttempts >= 10 &&
      (snapshot.blocking.topAppShare ?? 1) < 0.5,
  },
  {
    id: 'QUIET_WIN',
    category: 'hidden',
    title: 'Quiet win',
    description: 'A full hour of focus with no blocked-app attempts.',
    icon: 'eye-off-outline',
    hidden: true,
    condition: (lifetime, snapshot) =>
      lifetime.totalFocusMinutes >= 60 &&
      snapshot.sessions.cleanCount > 0 &&
      snapshot.blocking.totalAttempts === 0,
  },
];

export function evaluateAchievements(
  lifetime: LifetimeStats,
  snapshot: AnalyticsSnapshot,
): AchievementDefinition[] {
  return ACHIEVEMENTS.filter((achievement) => achievement.condition(lifetime, snapshot));
}

export async function syncAchievements(
  lifetime: LifetimeStats,
  snapshot: AnalyticsSnapshot,
): Promise<AchievementState> {
  const earnedIds = await dbGetEarnedAchievementIds();
  const eligible = evaluateAchievements(lifetime, snapshot);
  const earnedSet = new Set(earnedIds);
  const newlyEarnedIds = eligible
    .map((achievement) => achievement.id)
    .filter((id) => !earnedSet.has(id));
  await dbRecordEarnedAchievements(newlyEarnedIds);
  const nextEarnedIds = [...new Set([...earnedIds, ...newlyEarnedIds])];
  return {
    definitions: ACHIEVEMENTS.filter((achievement) => nextEarnedIds.includes(achievement.id)),
    earnedIds: nextEarnedIds,
    newlyEarnedIds,
  };
}