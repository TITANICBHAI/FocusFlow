import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

const appContext = readFileSync(
  path.resolve(__dirname, '../../src/context/AppContext.tsx'),
  'utf8',
);
const focusService = readFileSync(
  path.resolve(__dirname, '../../src/services/focusService.ts'),
  'utf8',
);

describe('focus-session recovery contracts', () => {
  it('clears orphaned and resolved sessions while preserving the expiry grace period', () => {
    expect(appContext).toContain('if (!linkedTask) return true');
    expect(appContext).toContain("linkedTask.status === 'completed' || linkedTask.status === 'skipped'");
    expect(appContext).toContain('endMs + 5 * 60 * 1000');
  });

  it('keeps teardown bounded and clears state after native rejection', () => {
    expect(focusService).toContain("withTimeout(dbEndFocusSession(task.id), 5000, 'dbEndFocusSession')");
    expect(focusService).toContain("withTimeout(\n    ForegroundServiceModule.stopService");
    expect(focusService).toContain("withTimeout(\n    SharedPrefsModule.publishFocusSnapshot");
    expect(focusService).toContain('.catch(() => {})');
  });
});
