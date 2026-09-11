import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

const root = path.resolve(__dirname, '../..');
const appContext = readFileSync(path.join(root, 'src/context/AppContext.tsx'), 'utf8');
const focusScreen = readFileSync(path.join(root, 'app/(tabs)/focus.tsx'), 'utf8');
const activeScreen = readFileSync(path.join(root, 'app/active.tsx'), 'utf8');
const defenseScreen = readFileSync(path.join(root, 'app/(tabs)/defense.tsx'), 'utf8');
const editTaskModal = readFileSync(path.join(root, 'src/components/EditTaskModal.tsx'), 'utf8');
const defaultSettings = readFileSync(path.join(root, 'src/data/defaultSettings.ts'), 'utf8');

function functionBody(signature: string, nextSignature: string): string {
  const start = appContext.indexOf(signature);
  const end = appContext.indexOf(nextSignature, start);
  expect(start).toBeGreaterThanOrEqual(0);
  expect(end).toBeGreaterThan(start);
  return appContext.slice(start, end);
}

describe('bug-fix phase contracts', () => {
  it('keeps Auto Reschedule disabled by default and gates every mutation', () => {
    expect(defaultSettings).toContain('autoRescheduleEnabled: false');

    const deleteTask = functionBody(
      'const deleteTask = useCallback(',
      '// Guard against concurrent extend calls',
    );
    const completeTask = functionBody(
      'const completeTask = useCallback(',
      'const skipTask = useCallback(',
    );
    const skipTask = functionBody(
      'const skipTask = useCallback(',
      'const extendTaskTime = useCallback(',
    );

    expect(completeTask).toContain('stateRef.current.settings.autoRescheduleEnabled');
    expect(completeTask).toContain('? compressSchedule(updated, compressionTime, tasksWithUpdate)');
    expect(completeTask).toContain(': tasksWithUpdate');
    expect(skipTask).toContain('stateRef.current.settings.autoRescheduleEnabled');
    expect(skipTask).toContain('? compressSchedule(updated, skippedAt, tasksWithUpdate)');
    expect(skipTask).toContain(': tasksWithUpdate');
    expect(deleteTask).toContain('stateRef.current.settings.autoRescheduleEnabled');
    expect(deleteTask).toContain('? compressDeletedTaskGap(task, tasks)');
    expect(deleteTask).toContain(': tasks');
  });

  it('uses authorized internal cleanup for completion, skipping, and orphan reconciliation', () => {
    const completeTask = functionBody(
      'const completeTask = useCallback(',
      'const skipTask = useCallback(',
    );
    const skipTask = functionBody(
      'const skipTask = useCallback(',
      'const extendTaskTime = useCallback(',
    );

    expect(completeTask).toContain('await stopFocusModeInternal()');
    expect(skipTask).toContain('await stopFocusModeInternal()');
    expect(appContext).toContain('void stopFocusModeInternal().catch');
  });

  it('forwards verified session PIN hashes and gates emergency override logging', () => {
    expect(focusScreen).toContain('stopFocusMode(hash)');
    expect(activeScreen).toContain('stopFocusMode(hash)');
    expect(focusScreen).toContain('const pinSet = await SessionPinModule.isPinSet()');
    expect(focusScreen).toContain('pendingFocusStopAction.current = stopAndLog');
    expect(focusScreen.indexOf('const stopAndLog')).toBeLessThan(
      focusScreen.indexOf('await dbLogFocusOverride'),
    );
  });

  it('gates disabling full-duration focus with the session PIN', () => {
    expect(defenseScreen).toContain('const requireSessionPin');
    expect(defenseScreen).toContain('SessionPinModule.isPinSet()');
    expect(defenseScreen).toContain('requireSessionPin(');
    expect(defenseScreen).toContain('keepFocusActiveUntilTaskEnd: false');
    expect(defenseScreen).toContain('keepFocusActiveUntilTaskEnd: true');
  });

  it('validates the session PIN and cleans up an active task session before deletion', () => {
    const deleteTask = functionBody(
      'const deleteTask = useCallback(',
      '// Guard against concurrent extend calls',
    );
    const pinValidation = deleteTask.indexOf('if (pinSet)');
    const sessionCleanup = deleteTask.indexOf('await stopFocusModeInternal()');
    const rowDelete = deleteTask.indexOf('await dbDeleteTask(taskId)');

    expect(deleteTask).toContain('SessionPinModule.verifyPin(pinHash)');
    expect(deleteTask).toContain('throw new Error(\'SESSION_PIN_REQUIRED\')');
    expect(pinValidation).toBeGreaterThanOrEqual(0);
    expect(sessionCleanup).toBeGreaterThan(pinValidation);
    expect(rowDelete).toBeGreaterThan(sessionCleanup);
  });

  it('confirms before replacing an existing focus session', () => {
    expect(appContext).toContain('const existingSession = currentState.focusSession');
    expect(appContext).toContain('const nativeFocusActive = isFocusActive()');
    expect(appContext).toContain('Focus is currently running for');
    expect(appContext).toContain('{ text: \'Cancel\', style: \'cancel\'');
    expect(appContext).toContain('{ text: \'Replace\', style: \'destructive\'');
    expect(appContext).toContain('if (!shouldReplace) return;');
  });

  it('supports custom task duration input and recalculates the task end time', () => {
    expect(editTaskModal).toContain('Custom');
    expect(editTaskModal).toContain('onPress={() => setCustomDuration(true)}');
    expect(editTaskModal).toContain('keyboardType="number-pad"');
    expect(editTaskModal).toContain('isNaN(duration) || duration < 5');
    expect(editTaskModal).toContain('const newEnd = newStart.add(duration, \'minute\')');
    expect(editTaskModal).toContain('durationMinutes: duration');
  });
});