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
  // ── Existing ────────────────────────────────────────────────────────────────

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
      const averageAbsoluteError =
        errors.reduce((sum, value) => sum + Math.abs(value), 0) / errors.length;
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

  // ── New ─────────────────────────────────────────────────────────────────────

  {
    id: 'IRON_SESSION',
    category: 'resistance',
    title: 'Iron session',
    description: 'Completed a focus session without opening a blocked app once.',
    icon: 'flash-outline',
    // Per-session achievement: fires as soon as ONE clean session appears in the
    // current window, distinguishing it from RESISTANCE_10_CLEAN_SESSIONS which
    // requires 10 lifetime clean sessions.
    condition: (_lifetime, snapshot) => snapshot.sessions.cleanCount > 0,
  },
  {
    id: 'THREE_WEEKS',
    category: 'presence',
    title: 'Three weeks',
    description: 'You have kept your streak going for at least 21 days.',
    icon: 'trophy-outline',
    // Uses the existing currentStreakDays field — no new LifetimeStats field needed.
    condition: (lifetime) => lifetime.currentStreakDays >= 21,
  },
  {
    id: 'LONG_GAME',
    category: 'hidden',
    title: 'The long game',
    description: 'Finished three or more tasks well ahead of schedule.',
    icon: 'hourglass-outline',
    hidden: true,
    // Negative estimationErrorMinutes means the task finished early. -30 means
    // finished 30 minutes ahead of the estimate. Uses estimationErrorMinutes
    // already in AnalyticsSnapshot.tasks — no new field needed.
    condition: (_lifetime, snapshot) =>
      snapshot.tasks.estimationErrorMinutes.filter((e) => e <= -30).length >= 3,
  },
  // SELF_AWARE (pattern_breaking) — SKIPPED.
  // Requires knowing when an app was added to the block list. All block-list
  // structures in types.ts (standaloneBlockPackages: string[],
  // alwaysOnPackages?: string[], blockedWords: string[],
  // RecurringBlockSchedule fields) are plain arrays or objects with no
  // changed_at / addedAt timestamp. No field exists to detect recency of
  // a block-list change, so this achievement cannot be implemented faithfully.
  {
    id: 'BACK_AGAIN',
    category: 'presence',
    title: 'Back again',
    description: 'You returned after more than five days away.',
    icon: 'return-up-forward-outline',
    // Requires lifetime.lastSessionAt — new field added to LifetimeStats.
    // Condition fires when the gap between the most recent completed session
    // and the current snapshot.generatedAt exceeds 5 days. Intended to be
    // evaluated at the moment a new session starts (call syncAchievements()
    // before inserting the new session so lastSessionAt reflects the previous one).
    condition: (lifetime, snapshot) => {
      if (!lifetime.lastSessionAt) return false;
      const lastMs = new Date(lifetime.lastSessionAt).getTime();
      const generatedMs = new Date(snapshot.generatedAt).getTime();
      return (generatedMs - lastMs) / (1000 * 60 * 60 * 24) > 5;
    },
  },
  {
    id: 'RESET',
    category: 'hidden',
    title: 'Reset',
    description: 'Came back after a long break and completed something.',
    icon: 'refresh-outline',
    hidden: true,
    // Same lastSessionAt field as BACK_AGAIN, plus snapshot.tasks.completed >= 1
    // to confirm the user actually did something after the gap (not just opened
    // the app). snapshot.tasks.completed is an existing field in AnalyticsSnapshot.
    condition: (lifetime, snapshot) => {
      if (!lifetime.lastSessionAt) return false;
      const lastMs = new Date(lifetime.lastSessionAt).getTime();
      const generatedMs = new Date(snapshot.generatedAt).getTime();
      const gapDays = (generatedMs - lastMs) / (1000 * 60 * 60 * 24);
      return gapDays > 5 && snapshot.tasks.completed >= 1;
    },
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
