import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const root = path.resolve(__dirname, '../..');
const database = readFileSync(path.join(root, 'src/data/database.ts'), 'utf8');
const engine = readFileSync(path.join(root, 'src/services/insightEngine.ts'), 'utf8');
const report = readFileSync(path.join(root, 'app/report.tsx'), 'utf8');
const stats = readFileSync(path.join(root, 'app/(tabs)/stats.tsx'), 'utf8');

describe('generated reporting contracts', () => {
  it('keeps generated analysis pure and offline', () => {
    expect(engine).not.toContain('fetch(');
    expect(engine).not.toContain('axios');
    expect(report).toContain('computeDailyAnalysis');
    expect(report).toContain('computeWeeklyAnalysis');
    expect(report).toContain('dbGetTasksInDateRange');
  });

  it('uses bounded report queries and saves optional notes on blur', () => {
    expect(report).toContain('range.start.toISOString()');
    expect(report).toContain('range.end.toISOString()');
    expect(report).toContain('getWeekStart(weekStartDay, anchor)');
    expect(report).toContain('onBlur={() => { void saveNote(); }}');
    expect(report).toContain("dbSaveReportNote(refDate, type, trimmed)");
  });

  it('renders the narrative before the summary card', () => {
    const analysisIndex = report.indexOf('THE TAKEAWAY');
    const summaryIndex = report.indexOf('>Summary<');
    expect(analysisIndex).toBeGreaterThanOrEqual(0);
    expect(summaryIndex).toBeGreaterThan(analysisIndex);
  });

  it('prunes old day and week notes with separate retention windows', () => {
    expect(database).toContain("DELETE FROM report_notes WHERE type = 'day' AND ref_date < ?");
    expect(database).toContain("DELETE FROM report_notes WHERE type = 'week' AND ref_date < ?");
    expect(database).toContain("dayjs().subtract(8, 'day')");
    expect(database).toContain("dayjs().subtract(14, 'day')");
  });

  it('keeps stats insights passive and provides both entry states', () => {
    expect(stats).toContain('<InsightsPanel');
    expect(stats).toContain('Weekly insights unlock soon');
    expect(stats).toContain("pathname: '/report'");
  });
});
