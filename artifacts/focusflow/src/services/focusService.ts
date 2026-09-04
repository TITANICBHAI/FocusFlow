/**
 * Focus Mode Service
 *
 * Handles JS-side orchestration of focus sessions. The actual enforcement
 * (app blocking, overlay) is done entirely by AppBlockerAccessibilityService (Kotlin).
 */

import { AppState, type AppStateStatus } from 'react-native';
import { dismissPersistentNotification } from './notificationService';
import { dbStartFocusSession, dbEndFocusSession } from '@/data/database';
import { ForegroundServiceModule } from '@/native-modules/ForegroundServiceModule';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { ForegroundLaunchModule } from '@/native-modules/ForegroundLaunchModule';
import { getUpcomingTask } from './taskService';
import type { Task, FocusSession } from '@/data/types';

// ─── State ────────────────────────────────────────────────────────────────────

let focusActive = false;
let currentTask: Task | null = null;
let appStateSubscription: ReturnType<typeof AppState.addEventListener> | null = null;
let onFocusViolation: ((appName: string) => void) | null = null;

// ─── Public API ───────────────────────────────────────────────────────────────

export async function startFocusMode(
  task: Task,
  allowedExtras: string[] = [],
  onViolation?: (appName: string) => void,
  options: { skipGoHome?: boolean } = {},
  allTasks: Task[] = [],
): Promise<void> {
  // Always clean up any existing subscription unconditionally before re-entering
  // (fixes NEW-019: subscription leaks when stopFocusMode short-circuits)
  appStateSubscription?.remove();
  appStateSubscription = null;

  if (focusActive) await stopFocusMode();

  const session: FocusSession = {
    taskId: task.id,
    startedAt: new Date().toISOString(),
    isActive: true,
    allowedPackages: allowedExtras,
  };

  await dbStartFocusSession(session);

  const nextTask = getUpcomingTask(allTasks.length > 1 ? allTasks : [task]);
  const startMs = new Date(task.startTime).getTime();
  const endMs = new Date(task.endTime).getTime();

  await ForegroundServiceModule.startService(task.id, task.title, startMs, endMs, nextTask?.title ?? null);
  await ForegroundServiceModule.requestBatteryOptimizationExemption();

  // Send the user to the home screen so focus mode starts with a clean slate,
  // unless the caller opted out (e.g. auto-start when app is already open).
  if (!options.skipGoHome) {
    await ForegroundLaunchModule.goHome();
  }

  // Publish the complete native state in one commit so the
  // AccessibilityService and BootReceiver never observe a partial session.
  // An empty whitelist means "block all during focus" — we pass a sentinel package
  // name that will never be installed so the Kotlin service denies all foreground apps.
  const filteredAllowed = session.allowedPackages.filter((p) => p.includes('.'));
  await SharedPrefsModule.publishFocusSnapshot(
    true,
    task.id,
    task.title,
    endMs,
    task.color ?? '',
    filteredAllowed.length > 0 ? filteredAllowed : ['com.focusflow.internal.blockall'],
    nextTask?.title ?? null,
    null,
  );

  // App blocking is handled entirely by AppBlockerAccessibilityService (Kotlin).
  // It reads the atomically published focus_active/task/allowed state from
  // SharedPreferences and intercepts window changes at the OS level.
  focusActive = true;
  currentTask = task;
  onFocusViolation = onViolation ?? null;

  appStateSubscription = AppState.addEventListener('change', handleAppStateChange);
}

export async function stopFocusMode(pinHash: string | null = null): Promise<void> {
  // Always clear native state unconditionally so a cold-start zombie session
  // (focusActive=false in JS but focus_active=true in SharedPreferences from a
  // previous run) is always cleaned up regardless of the JS-side flag.
  const hadActiveSession = focusActive && currentTask !== null;

  focusActive = false;
  const task = currentTask;
  currentTask = null;
  onFocusViolation = null;

  appStateSubscription?.remove();
  appStateSubscription = null;

  // Always clear Kotlin-side state so the AccessibilityService stops blocking
  // even if the JS module was freshly initialised (cold-start recovery).
  await ForegroundServiceModule.stopService(pinHash).catch(() => {});
  await SharedPrefsModule.publishFocusSnapshot(false, null, null, 0, null, [], null, pinHash).catch(() => {});

  // Only hit the DB if we had a real session — avoids a spurious DB write on
  // cold-start cleanup where there is no matching open session row.
  if (hadActiveSession && task) {
    await dbEndFocusSession(task.id);
    await dismissPersistentNotification();
  }
}

export function isFocusActive(): boolean {
  return focusActive;
}

export function getCurrentFocusTask(): Task | null {
  return currentTask;
}

/**
 * Keeps the module-level session snapshot aligned when the active task is
 * edited or extended while focus mode is running.
 */
export function updateCurrentFocusTask(task: Task): void {
  if (currentTask?.id === task.id) {
    currentTask = task;
  }
}

// ─── App State Handling ───────────────────────────────────────────────────────

function handleAppStateChange(_state: AppStateStatus): void {
  // App blocking is handled entirely by AppBlockerAccessibilityService (Kotlin).
  // No JS-side nudge notifications are needed — they would be dismissable and
  // create a false sense of enforcement.
}
