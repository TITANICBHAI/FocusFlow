import { beforeEach, describe, expect, it, vi } from 'vitest';

const sqliteMock = vi.hoisted(() => ({
  openDatabaseAsync: vi.fn(),
  deleteDatabaseAsync: vi.fn(async () => undefined),
}));

const loggerMock = vi.hoisted(() => ({
  logger: {
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock('expo-sqlite', () => sqliteMock);
vi.mock('@/services/startupLogger', () => loggerMock);
vi.mock('react-native', () => ({
  Platform: {
    Version: 35,
    constants: { Release: 'test', Manufacturer: 'test', Model: 'test' },
  },
  Appearance: {
    getColorScheme: () => 'light',
  },
}));

import {
  dbGetEstimationErrors,
  dbGetSessionsWithOverrideCount,
  dbGetTasksByHourOfDay,
  dbGetWeeklyCompletionRates,
  resetDb,
} from '@/data/database';

type FakeDatabase = {
  runAsync: ReturnType<typeof vi.fn>;
  getFirstAsync: ReturnType<typeof vi.fn>;
  getAllAsync: ReturnType<typeof vi.fn>;
};

let database: FakeDatabase;

beforeEach(() => {
  database = {
    runAsync: vi.fn(async (sql: string) => {
      if (sql.includes('ALTER TABLE tasks ADD COLUMN focus_allowed_packages')) {
        throw new Error('duplicate column');
      }
    }),
    getFirstAsync: vi.fn(async (sql: string) => {
      if (sql.includes('sqlite_version')) return { v: '3.45.0' };
      if (sql.includes('journal_mode')) return { journal_mode: 'wal' };
      return { n: 0 };
    }),
    getAllAsync: vi.fn(async () => []),
  };
  sqliteMock.openDatabaseAsync.mockResolvedValue(database);
  resetDb();
  vi.clearAllMocks();
  sqliteMock.openDatabaseAsync.mockResolvedValue(database);
});

describe('analytics database queries', () => {
  it('passes an exclusive end boundary to the session override query', async () => {
    const rows = [{
      session_id: 12,
      task_id: 'task-12',
      started_at: '2026-09-08T10:00:00.000Z',
      ended_at: '2026-09-08T10:30:00.000Z',
      override_count: 0,
    }];
    database.getAllAsync.mockResolvedValueOnce(rows);

    await expect(dbGetSessionsWithOverrideCount(
      '2026-09-08T00:00:00.000Z',
      '2026-09-09T00:00:00.000Z',
    )).resolves.toEqual(rows);

    const [sql, params] = database.getAllAsync.mock.calls.at(-1)!;
    expect(sql).toContain('o.overridden_at < ?');
    expect(sql).toContain('s.started_at < ?');
    expect(sql).toContain('(s.ended_at IS NULL OR s.ended_at > ?)');
    expect(params).toEqual([
      '2026-09-09T00:00:00.000Z',
      '2026-09-08T00:00:00.000Z',
      '2026-09-09T00:00:00.000Z',
      '2026-09-09T00:00:00.000Z',
      '2026-09-08T00:00:00.000Z',
    ]);
  });

  it('excludes open, zero-duration, and invalid estimation sessions', async () => {
    await expect(dbGetEstimationErrors(
      '2026-09-08T00:00:00.000Z',
      '2026-09-09T00:00:00.000Z',
    )).resolves.toEqual([]);

    const [sql, params] = database.getAllAsync.mock.calls.at(-1)!;
    expect(sql).toContain("t.status = 'completed'");
    expect(sql).toContain('s.ended_at IS NOT NULL');
    expect(sql).toContain('t.duration_minutes > 0');
    expect(sql).toContain('s.ended_at > s.started_at');
    expect(params).toEqual([
      '2026-09-09T00:00:00.000Z',
      '2026-09-08T00:00:00.000Z',
    ]);
  });

  it('returns empty aggregates without inventing rows and preserves hour boundaries', async () => {
    await expect(dbGetTasksByHourOfDay(
      '2026-09-08T00:00:00.000Z',
      '2026-09-09T00:00:00.000Z',
    )).resolves.toEqual([]);

    const [hourSql, hourParams] = database.getAllAsync.mock.calls.at(-1)!;
    expect(hourSql).toContain("strftime('%H', datetime(start_time, 'localtime'))");
    expect(hourParams).toEqual([
      '2026-09-08T00:00:00.000Z',
      '2026-09-09T00:00:00.000Z',
    ]);

    await expect(dbGetWeeklyCompletionRates(99)).resolves.toEqual([]);
    const [weekSql, weekParams] = database.getAllAsync.mock.calls.at(-1)!;
    expect(weekSql).toContain('FROM daily_completions');
    expect(weekSql).toContain("strftime('%w', date)");
    expect(weekParams).toHaveLength(1);
    expect(String(weekParams[0])).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});