import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const appContext = readFileSync(
  path.resolve(__dirname, '../../src/context/AppContext.tsx'),
  'utf8',
);

function functionBody(signature: string, nextSignature: string): string {
  const start = appContext.indexOf(signature);
  const end = appContext.indexOf(nextSignature, start);
  expect(start).toBeGreaterThanOrEqual(0);
  expect(end).toBeGreaterThan(start);
  return appContext.slice(start, end);
}

describe('task action safety contracts', () => {
  it('cancels and dismisses the OS alarm before completing a task', () => {
    const completeTask = functionBody(
      'const completeTask = useCallback(',
      'const skipTask = useCallback(',
    );
    const cancel = completeTask.indexOf('await TaskAlarmModule.cancelAlarm(taskId)');
    const dismiss = completeTask.indexOf('void TaskAlarmModule.dismissAlarm(taskId)');

    expect(cancel).toBeGreaterThanOrEqual(0);
    expect(dismiss).toBeGreaterThan(cancel);
    expect(completeTask).toContain('await stopFocusMode()');
  });

  it('cancels the OS alarm and stops focus when skipping its active task', () => {
    const skipTask = functionBody(
      'const skipTask = useCallback(',
      'const extendTaskTime = useCallback(',
    );
    const dispatch = skipTask.indexOf(
      "dispatch({ type: 'SET_TASKS', payload: compressed });",
    );
    const cancel = skipTask.indexOf('await TaskAlarmModule.cancelAlarm(taskId)');
    const dismiss = skipTask.indexOf('void TaskAlarmModule.dismissAlarm(taskId)');
    const focusStop = skipTask.indexOf('await stopFocusMode()');

    expect(cancel).toBeGreaterThanOrEqual(0);
    expect(dismiss).toBeGreaterThan(cancel);
    expect(focusStop).toBeGreaterThan(dispatch);
    expect(skipTask).toContain(
      'if (stateRef.current.focusSession?.taskId === taskId)',
    );
  });
});