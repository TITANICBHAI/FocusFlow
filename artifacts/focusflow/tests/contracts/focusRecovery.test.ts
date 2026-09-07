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
const activeScreen = readFileSync(
  path.resolve(__dirname, '../../app/active.tsx'),
  'utf8',
);
const focusScreen = readFileSync(
  path.resolve(__dirname, '../../app/(tabs)/focus.tsx'),
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

  it('keeps an orphaned session stoppable from both focus surfaces', () => {
    expect(activeScreen).toContain('A focus session is active, but its task could not be found.');
    expect(activeScreen).toContain('>{focusActive ? (');
    expect(focusScreen).toContain('!task && isFocusing');
    expect(focusScreen).toContain('stopFocusMode()');
  });

  it('persists dismissal and caps open-session totals defensively', () => {
    expect(appContext).toContain("focus_dismissed_task_id");
    expect(appContext).toContain('dismissedFocusTaskIdRef.current');
    const database = readFileSync(
      path.resolve(__dirname, '../../src/data/database.ts'),
      'utf8',
    );
    expect(database).toContain('const MAX_SESSION_MS = 6 * 60 * 60 * 1000');
    expect(database).toContain('Math.min(Math.max(0, end - start), MAX_SESSION_MS)');
  });
});
