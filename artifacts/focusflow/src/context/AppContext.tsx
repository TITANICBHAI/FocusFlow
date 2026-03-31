import React, {
  createContext,
  useContext,
  useEffect,
  useReducer,
  useCallback,
  useRef,
} from 'react';
import { Alert } from 'react-native';
import type { Task, AppSettings, FocusSession } from '@/data/types';
import {
  dbGetTasksForDate,
  dbInsertTask,
  dbUpdateTask,
  dbDeleteTask,
  dbGetSettings,
  dbSaveSettings,
  dbGetActiveFocusSession,
} from '@/data/database';
import {
  getTodayTasks,
  getActiveTask,
  extendTask,
  updateTaskStatus,
} from '@/services/taskService';
import {
  rebalanceAfterOverrun,
  getUnfinishedOverdueTasks,
} from '@/services/schedulerEngine';
import {
  scheduleTaskReminders,
  cancelTaskReminders,
  setupNotificationChannels,
  requestPermissions,
} from '@/services/notificationService';
import {
  startFocusMode as _startFocusMode,
  stopFocusMode as _stopFocusMode,
  isFocusActive,
} from '@/services/focusService';
import { SharedPrefsModule } from '@/native-modules/SharedPrefsModule';
import { ForegroundServiceModule } from '@/native-modules/ForegroundServiceModule';
import { EventBridge } from '@/services/eventBridge';

// ─── State ────────────────────────────────────────────────────────────────────

interface AppState {
  tasks: Task[];
  settings: AppSettings;
  focusSession: FocusSession | null;
  focusViolationApp: string | null;
  isLoading: boolean;
  isDbReady: boolean;
}

type AppAction =
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_DB_READY' }
  | { type: 'SET_TASKS'; payload: Task[] }
  | { type: 'ADD_TASK'; payload: Task }
  | { type: 'UPDATE_TASK'; payload: Task }
  | { type: 'DELETE_TASK'; payload: string }
  | { type: 'SET_SETTINGS'; payload: AppSettings }
  | { type: 'SET_FOCUS_SESSION'; payload: FocusSession | null }
  | { type: 'SET_FOCUS_VIOLATION'; payload: string | null };

function reducer(state: AppState, action: AppAction): AppState {
  switch (action.type) {
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    case 'SET_DB_READY':
      return { ...state, isDbReady: true };
    case 'SET_TASKS':
      return { ...state, tasks: action.payload };
    case 'ADD_TASK':
      return { ...state, tasks: [...state.tasks, action.payload] };
    case 'UPDATE_TASK':
      return {
        ...state,
        tasks: state.tasks.map((t) => (t.id === action.payload.id ? action.payload : t)),
      };
    case 'DELETE_TASK':
      return { ...state, tasks: state.tasks.filter((t) => t.id !== action.payload) };
    case 'SET_SETTINGS':
      return { ...state, settings: action.payload };
    case 'SET_FOCUS_SESSION':
      return { ...state, focusSession: action.payload };
    case 'SET_FOCUS_VIOLATION':
      return { ...state, focusViolationApp: action.payload };
    default:
      return state;
  }
}

const defaultSettings: AppSettings = {
  darkMode: false,
  defaultDuration: 60,
  defaultReminderOffsets: [-10, -5, 0],
  focusModeEnabled: true,
  allowedInFocus: [
    'com.android.phone',
    'com.android.dialer',
    'com.google.android.dialer',
    'com.whatsapp',
    'com.samsung.android.incallui',
  ],
  pomodoroEnabled: false,
  pomodoroDuration: 25,
  pomodoroBreak: 5,
  notificationsEnabled: true,
  onboardingComplete: false,
  standaloneBlockPackages: [],
  standaloneBlockUntil: null,
};

const initialState: AppState = {
  tasks: [],
  settings: defaultSettings,
  focusSession: null,
  focusViolationApp: null,
  isLoading: true,
  isDbReady: false,
};

// ─── Context ──────────────────────────────────────────────────────────────────

interface AppContextValue {
  state: AppState;
  todayTasks: Task[];
  activeTask: Task | null;

  addTask: (task: Task) => Promise<void>;
  updateTask: (task: Task) => Promise<void>;
  deleteTask: (taskId: string) => Promise<void>;
  completeTask: (taskId: string) => Promise<void>;
  skipTask: (taskId: string) => Promise<void>;
  extendTaskTime: (taskId: string, extraMinutes: number) => Promise<void>;

  startFocusMode: (taskId: string) => Promise<void>;
  stopFocusMode: () => Promise<void>;

  updateSettings: (settings: AppSettings) => Promise<void>;
  setStandaloneBlock: (packages: string[], untilMs: number | null) => Promise<void>;
  refreshTasks: () => Promise<void>;
}

const AppContext = createContext<AppContextValue | null>(null);

// ─── Provider ─────────────────────────────────────────────────────────────────

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const tickRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── Initialize ──────────────────────────────────────────────────────────────

  useEffect(() => {
    void init();
    return () => {
      if (tickRef.current) clearInterval(tickRef.current);
    };
  }, []);

  async function init() {
    dispatch({ type: 'SET_LOADING', payload: true });
    try {
      await setupNotificationChannels();
      await requestPermissions();

      // Ensure the foreground service is always running from the first app open.
      // This keeps the process alive so Android cannot kill the AccessibilityService.
      await ForegroundServiceModule.startIdleService();

      const settings = await dbGetSettings();
      dispatch({ type: 'SET_SETTINGS', payload: settings });
      dispatch({ type: 'SET_DB_READY' });

      // Re-apply standalone block from persisted settings on startup.
      await _syncStandaloneBlock(settings);

      await refreshTasks();

      // Recover tasks that were still "scheduled" when the app was killed/restarted
      const allTasks = await dbGetTasksForDate(new Date().toISOString());
      const overdue = getUnfinishedOverdueTasks(allTasks);
      for (const t of overdue) {
        const marked = updateTaskStatus(t, 'overdue');
        await dbUpdateTask(marked);
      }
      if (overdue.length > 0) await refreshTasks();

      const activeSession = await dbGetActiveFocusSession();
      if (activeSession) {
        dispatch({ type: 'SET_FOCUS_SESSION', payload: activeSession });
      }
    } catch (e) {
      console.error('[AppContext] init error', e);
    } finally {
      dispatch({ type: 'SET_LOADING', payload: false });
    }
  }

  /**
   * Syncs standalone block state from settings into SharedPreferences.
   * Clears the block if it has already expired.
   */
  async function _syncStandaloneBlock(settings: AppSettings): Promise<void> {
    const { standaloneBlockPackages, standaloneBlockUntil } = settings;
    const packages = standaloneBlockPackages ?? [];
    if (packages.length === 0 || !standaloneBlockUntil) {
      await SharedPrefsModule.setStandaloneBlock(false, [], 0);
      return;
    }
    const untilMs = new Date(standaloneBlockUntil).getTime();
    if (untilMs <= Date.now()) {
      // Expired — clear from SharedPrefs and settings
      await SharedPrefsModule.setStandaloneBlock(false, [], 0);
      const cleared = { ...settings, standaloneBlockPackages: [], standaloneBlockUntil: null };
      await dbSaveSettings(cleared);
      dispatch({ type: 'SET_SETTINGS', payload: cleared });
    } else {
      await SharedPrefsModule.setStandaloneBlock(true, packages, untilMs);
    }
  }

  // ── Keep a ref to latest state so the tick interval never needs to re-create ─
  // (fixes NEW-021: setInterval restarting on every task/settings change)
  const stateRef = useRef(state);
  useEffect(() => {
    stateRef.current = state;
  });

  // ── Tick: check active tasks every 30s ──────────────────────────────────────
  // Only recreated when DB readiness changes — state is read via stateRef.

  useEffect(() => {
    if (!state.isDbReady) return;
    tickRef.current = setInterval(() => {
      const active = getActiveTask(stateRef.current.tasks);
      // NOTE: The native ForegroundTaskService already shows a persistent
      // notification while focus is active (NEW-011). No JS sticky needed here.
      if (!active && isFocusActive()) {
        void _stopFocusMode();
        dispatch({ type: 'SET_FOCUS_SESSION', payload: null });
      }
    }, 30000);
    return () => {
      if (tickRef.current) clearInterval(tickRef.current);
    };
  }, [state.isDbReady]);

  // ── Auto-start focus mode when active task has focusMode: true ───────────────
  // Runs when the active task changes. If the task was created with focusMode
  // enabled and no focus session is running yet, activate silently (no goHome).

  useEffect(() => {
    const active = getActiveTask(stateRef.current.tasks);
    if (active && active.focusMode && stateRef.current.focusSession === null && !isFocusActive()) {
      void _startFocusMode(
        active,
        stateRef.current.settings.allowedInFocus,
        (app) => {
          dispatch({ type: 'SET_FOCUS_VIOLATION', payload: app });
          setTimeout(() => dispatch({ type: 'SET_FOCUS_VIOLATION', payload: null }), 4000);
        },
        { skipGoHome: true },
      ).then(() => {
        const session: FocusSession = {
          taskId: active.id,
          startedAt: new Date().toISOString(),
          isActive: true,
          allowedPackages: stateRef.current.settings.allowedInFocus,
        };
        dispatch({ type: 'SET_FOCUS_SESSION', payload: session });
      }).catch(() => {});
    }
  // Re-run when the active task identity changes (new task or task cleared)
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [state.tasks]);

  // ── Native event subscriptions ───────────────────────────────────────────────

  useEffect(() => {
    const unsubTaskEnded = EventBridge.subscribe('TASK_ENDED', () => {
      void _stopFocusMode();
      dispatch({ type: 'SET_FOCUS_SESSION', payload: null });
    });

    const unsubAppBlocked = EventBridge.subscribe('APP_BLOCKED', (event) => {
      dispatch({ type: 'SET_FOCUS_VIOLATION', payload: event.blockedApp ?? null });
    });

    return () => {
      unsubTaskEnded();
      unsubAppBlocked();
    };
  }, []);

  // ── Tasks ───────────────────────────────────────────────────────────────────

  const refreshTasks = useCallback(async () => {
    const tasks = await dbGetTasksForDate(new Date().toISOString());
    dispatch({ type: 'SET_TASKS', payload: tasks });
  }, []);

  const addTask = useCallback(async (task: Task) => {
    await dbInsertTask(task);
    dispatch({ type: 'ADD_TASK', payload: task });
    await scheduleTaskReminders(task);
  }, []);

  const updateTask = useCallback(async (task: Task) => {
    await dbUpdateTask(task);
    dispatch({ type: 'UPDATE_TASK', payload: task });
    await cancelTaskReminders(task.id);
    await scheduleTaskReminders(task);
  }, []);

  const deleteTask = useCallback(async (taskId: string) => {
    await dbDeleteTask(taskId);
    await cancelTaskReminders(taskId);
    dispatch({ type: 'DELETE_TASK', payload: taskId });
  }, []);

  // Guard against concurrent extend calls — prevents stale-state double-writes.
  const extendingRef = useRef(false);

  const completeTask = useCallback(
    async (taskId: string) => {
      const tasks = stateRef.current.tasks;
      const task = tasks.find((t) => t.id === taskId);
      if (!task) return;
      const updated = updateTaskStatus(task, 'completed');
      await dbUpdateTask(updated);
      await cancelTaskReminders(taskId);
      dispatch({ type: 'UPDATE_TASK', payload: updated });
      if (stateRef.current.focusSession?.taskId === taskId) {
        await stopFocusMode();
      }
    },
    // stateRef is a stable ref — no deps needed
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  const skipTask = useCallback(
    async (taskId: string) => {
      const tasks = stateRef.current.tasks;
      const task = tasks.find((t) => t.id === taskId);
      if (!task) return;
      const updated = updateTaskStatus(task, 'skipped');
      await dbUpdateTask(updated);
      await cancelTaskReminders(taskId);
      dispatch({ type: 'UPDATE_TASK', payload: updated });
    },
    [],
  );

  const extendTaskTime = useCallback(
    async (taskId: string, extraMinutes: number) => {
      // Prevent concurrent extend calls — second tap is silently ignored.
      if (extendingRef.current) return;
      extendingRef.current = true;
      try {
        // Always read from the ref so we get the latest tasks even if the
        // closure was captured before a previous extend dispatch resolved.
        const tasks = stateRef.current.tasks;
        const task = tasks.find((t) => t.id === taskId);
        if (!task) return;

        const extended = extendTask(task, extraMinutes);

        const { updatedSchedule, needsUserConfirm, skipped } = rebalanceAfterOverrun(extended, extraMinutes, tasks);

        await dbUpdateTask(extended);
        for (const t of updatedSchedule) {
          if (t.id !== extended.id) await dbUpdateTask(t);
        }

        const updatedById = new Map(updatedSchedule.map((t) => [t.id, t]));
        const finalTasks = tasks.map((t) => {
          if (t.id === extended.id) return extended;
          return updatedById.get(t.id) ?? t;
        });
        dispatch({ type: 'SET_TASKS', payload: finalTasks });

        await cancelTaskReminders(taskId);
        await scheduleTaskReminders(extended);

        if (needsUserConfirm.length > 0) {
          const names = needsUserConfirm.map((t) => `• ${t.title}`).join('\n');
          Alert.alert(
            '⚠️ Critical Tasks Affected',
            `These high-priority tasks overlap with your extension and need your attention:\n\n${names}\n\nPlease review and reschedule them manually.`,
            [{ text: 'OK' }],
          );
        }
        if (skipped.length > 0) {
          const names = skipped.map((t) => `• ${t.title}`).join('\n');
          Alert.alert(
            'Tasks Auto-Skipped',
            `These lower-priority tasks were skipped to protect your schedule:\n\n${names}`,
            [{ text: 'OK' }],
          );
        }
      } finally {
        extendingRef.current = false;
      }
    },
    [],
  );

  // The native ForegroundTaskService fires NOTIF_ACTION when the user taps a
  // button on the persistent focus notification. We handle it here so the app
  // does not need to be open for task state to update.
  // NOTE: must live after completeTask / extendTaskTime / skipTask declarations
  // to avoid a const-TDZ error when the dependency array is evaluated.
  useEffect(() => {
    const unsubNotifAction = EventBridge.subscribe('NOTIF_ACTION', (event) => {
      const { notifAction, taskId, minutes } = event;
      if (!taskId || !notifAction) return;
      if (notifAction === 'COMPLETE') {
        void completeTask(taskId);
      } else if (notifAction === 'EXTEND') {
        void extendTaskTime(taskId, minutes ?? 15);
      } else if (notifAction === 'SKIP') {
        void skipTask(taskId);
      }
    });
    return () => { unsubNotifAction(); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [completeTask, extendTaskTime, skipTask]);

  // ── Focus Mode ──────────────────────────────────────────────────────────────

  const startFocusMode = useCallback(
    async (taskId: string) => {
      const task = state.tasks.find((t) => t.id === taskId);
      if (!task) return;

      await _startFocusMode(task, state.settings.allowedInFocus, (app) => {
        dispatch({ type: 'SET_FOCUS_VIOLATION', payload: app });
        setTimeout(() => dispatch({ type: 'SET_FOCUS_VIOLATION', payload: null }), 4000);
      });

      const session: FocusSession = {
        taskId: task.id,
        startedAt: new Date().toISOString(),
        isActive: true,
        allowedPackages: state.settings.allowedInFocus,
      };
      dispatch({ type: 'SET_FOCUS_SESSION', payload: session });
    },
    [state.tasks, state.settings.allowedInFocus],
  );

  const stopFocusMode = useCallback(async () => {
    await _stopFocusMode();
    dispatch({ type: 'SET_FOCUS_SESSION', payload: null });
  }, []);

  // ── Settings ─────────────────────────────────────────────────────────────────

  const updateSettings = useCallback(async (settings: AppSettings) => {
    await dbSaveSettings(settings);
    dispatch({ type: 'SET_SETTINGS', payload: settings });
    if (state.focusSession !== null) {
      await SharedPrefsModule.setAllowedPackages(
        settings.allowedInFocus.filter((p) => p.includes('.')),
      );
    }
    // Always sync standalone block — it works independently of task focus.
    await _syncStandaloneBlock(settings);
  }, [state.focusSession]);

  /**
   * Enable or disable standalone app blocking.
   * Writes to both the SQLite settings and SharedPreferences (native layer).
   *
   * @param packages  Package names to block (empty array = disable)
   * @param untilMs   Epoch ms when block expires (null = disable)
   */
  const setStandaloneBlock = useCallback(async (packages: string[], untilMs: number | null) => {
    const untilIso = untilMs ? new Date(untilMs).toISOString() : null;
    const newSettings: AppSettings = {
      ...state.settings,
      standaloneBlockPackages: packages,
      standaloneBlockUntil: untilIso,
    };
    await dbSaveSettings(newSettings);
    dispatch({ type: 'SET_SETTINGS', payload: newSettings });
    const active = packages.length > 0 && untilMs !== null && untilMs > Date.now();
    await SharedPrefsModule.setStandaloneBlock(active, packages, untilMs ?? 0);
  }, [state.settings]);

  // ── Derived ──────────────────────────────────────────────────────────────────

  const todayTasks = getTodayTasks(state.tasks);
  const activeTask = getActiveTask(state.tasks);

  const value: AppContextValue = {
    state,
    todayTasks,
    activeTask,
    addTask,
    updateTask,
    deleteTask,
    completeTask,
    skipTask,
    extendTaskTime,
    startFocusMode,
    stopFocusMode,
    updateSettings,
    setStandaloneBlock,
    refreshTasks,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppContextValue {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}
