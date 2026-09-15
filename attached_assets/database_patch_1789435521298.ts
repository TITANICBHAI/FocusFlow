// ─── PATCH for src/data/database.ts ──────────────────────────────────────────
//
// Replace the existing `export interface LifetimeStats` block and
// `export async function dbGetLifetimeStats()` function with the versions below.
// Everything else in database.ts is unchanged.
//
// ─────────────────────────────────────────────────────────────────────────────

export interface LifetimeStats {
  completedTasks: number;
  totalSessions: number;
  cleanSessions: number;
  totalFocusMinutes: number;
  totalOverrideAttempts: number;
  currentStreakDays: number;
  /**
   * ISO timestamp of the most recent **completed** focus session's `started_at`.
   * NULL when no completed session exists yet.
   *
   * Used by BACK_AGAIN and RESET achievements to detect a gap of > 5 days
   * between sessions. Computed from `MAX(started_at) FROM focus_sessions
   * WHERE is_active = 0` so that a currently-active session is excluded —
   * call `dbGetLifetimeStats()` *before* inserting the new session to get the
   * gap-before-this-session reading.
   */
  lastSessionAt: string | null;
}

export async function dbGetLifetimeStats(): Promise<LifetimeStats> {
  const nowISO = new Date().toISOString();
  const aggregate = await runWithDb('dbGetLifetimeStats', (database) =>
    database.getFirstAsync<{
      completed_tasks: number;
      total_sessions: number;
      clean_sessions: number;
      total_focus_minutes: number | null;
      total_override_attempts: number;
      last_session_at: string | null;   // ← new field
    }>(
      `SELECT
         (SELECT COUNT(*) FROM tasks WHERE status = 'completed') AS completed_tasks,
         (SELECT COUNT(*) FROM focus_sessions) AS total_sessions,
         (SELECT COUNT(*)\n            FROM focus_sessions s\n           WHERE NOT EXISTS (\n             SELECT 1\n               FROM focus_overrides o\n              WHERE o.task_id = s.task_id\n                AND o.overridden_at >= s.started_at\n                AND o.overridden_at <= COALESCE(s.ended_at, ?)\n           )) AS clean_sessions,
         (SELECT COALESCE(SUM(\n           CASE\n             WHEN s.ended_at IS NULL THEN 0\n             ELSE MAX(0, (julianday(s.ended_at) - julianday(s.started_at)) * 1440.0)\n           END\n         ), 0) FROM focus_sessions s) AS total_focus_minutes,
         (SELECT COUNT(*) FROM focus_overrides) AS total_override_attempts,
         (SELECT MAX(started_at) FROM focus_sessions WHERE is_active = 0) AS last_session_at`,
      [nowISO],
    ),
  );
  const currentStreakDays = await dbGetStreak();
  return {
    completedTasks: aggregate?.completed_tasks ?? 0,
    totalSessions: aggregate?.total_sessions ?? 0,
    cleanSessions: aggregate?.clean_sessions ?? 0,
    totalFocusMinutes: Math.round((aggregate?.total_focus_minutes ?? 0) * 100) / 100,
    totalOverrideAttempts: aggregate?.total_override_attempts ?? 0,
    currentStreakDays,
    lastSessionAt: aggregate?.last_session_at ?? null,   // ← new field
  };
}
