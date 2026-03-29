/**
 * App.tsx — FocusDay entry point
 *
 * IMPORTANT: The three sections below MUST run at module load time (top level),
 * BEFORE any React component renders. The OS re-launches this file headlessly
 * when waking JS for a background event, and it looks for task definitions
 * immediately. Defining them inside a component is too late.
 *
 * Section order:
 *   1. Background task definitions (imported side-effects from backgroundTasks.ts)
 *   2. Notification foreground handler
 *   3. Splash screen keep-alive
 *   4. Notification response handler (tapping a notification)
 *   5. React component tree
 */

// ─── 1. Register all background tasks with the OS ─────────────────────────────
// This import has side-effects: it calls TaskManager.defineTask() for every
// background task FocusDay needs. Must be the very first import.
import './src/tasks/backgroundTasks';

import React, { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import * as SplashScreen from 'expo-splash-screen';
import * as Notifications from 'expo-notifications';
import { StyleSheet } from 'react-native';
import { AppProvider } from '@/context/AppContext';
import AppNavigator from '@/navigation/AppNavigator';
import { EventBridge } from '@/services/eventBridge';
import { navigateToTask } from '@/navigation/navigationRef';
import { registerBackgroundFetch } from '@/tasks/backgroundTasks';

// ─── 2. Foreground notification display behaviour ─────────────────────────────
// Controls how notifications look when the app is open.
Notifications.setNotificationHandler({
  handleNotification: async (notification) => {
    const data = notification.request.content.data as { type?: string };
    // Don't pop persistent focus notifications as alerts — they live in the tray
    if (data?.type === 'focus-persistent') {
      return { shouldShowAlert: false, shouldPlaySound: false, shouldSetBadge: false };
    }
    return { shouldShowAlert: true, shouldPlaySound: true, shouldSetBadge: false };
  },
});

// ─── 3. Connect native event channel ─────────────────────────────────────────
// Wires FocusDayBridgeModule (Android) → JS event bus. No-op if module not linked.
EventBridge.init();

// ─── 4. Keep splash visible until the app context is ready ───────────────────
SplashScreen.preventAutoHideAsync();

// ─── 5. Notification response handler (tap or action button) ─────────────────
// Runs both in foreground and when app is cold-started by tapping a notification.
Notifications.addNotificationResponseReceivedListener((response) => {
  const data = response.notification.request.content.data as {
    taskId?: string;
    type?: string;
  };
  const actionId = response.actionIdentifier;

  if (!data?.taskId) return;

  if (
    actionId === Notifications.DEFAULT_ACTION_IDENTIFIER ||
    actionId === 'VIEW'
  ) {
    // User tapped the notification body — navigate to the task
    navigateToTask(data.taskId);
  }
  // 'COMPLETE' and 'EXTEND' action button presses are handled headlessly
  // by TASK_NOTIFICATION_BG in backgroundTasks.ts when the app is killed,
  // or by the foreground listener below when the app is open.
});

// ─── 6. Foreground notification action handler ────────────────────────────────
// Handles "Complete" / "Extend" action buttons when the app IS in the foreground.
// (The backgroundTasks.ts handler covers the killed/background case.)
Notifications.addNotificationReceivedListener((notification) => {
  const data = notification.request.content.data as {
    taskId?: string;
    type?: string;
  };
  if (data?.type === 'LATE_START_WARNING' && data.taskId) {
    // Could trigger an in-app alert — currently handled by ScheduleScreen polling
  }
});

// ─── 7. Set up notification action categories ─────────────────────────────────
// Action buttons that appear on notifications (iOS long-press; Android actions).
async function setupNotificationCategories() {
  await Notifications.setNotificationCategoryAsync('task-active', [
    {
      identifier: 'COMPLETE',
      buttonTitle: '✅ Complete',
      options: { opensAppToForeground: false },
    },
    {
      identifier: 'EXTEND',
      buttonTitle: '⏱ +15 min',
      options: { opensAppToForeground: false },
    },
    {
      identifier: 'VIEW',
      buttonTitle: '👁 View',
      options: { opensAppToForeground: true },
    },
  ]);

  await Notifications.setNotificationCategoryAsync('task-reminder', [
    {
      identifier: 'VIEW',
      buttonTitle: '👁 Open',
      options: { opensAppToForeground: true },
    },
    {
      identifier: 'COMPLETE',
      buttonTitle: '✅ Done',
      options: { opensAppToForeground: false },
    },
  ]);
}

// ─── React component ──────────────────────────────────────────────────────────

export default function App() {
  useEffect(() => {
    async function bootstrap() {
      // Set up notification action buttons
      await setupNotificationCategories();

      // Register periodic background fetch with the OS
      // (checks for tasks whose alarms may have been killed by the OEM)
      await registerBackgroundFetch();

      // Request battery optimisation exemption
      // (prevents OEM task killers from stopping the foreground service)
      try {
        const { ForegroundServiceModule } = await import('@/native-modules/ForegroundServiceModule');
        await ForegroundServiceModule.requestBatteryOptimizationExemption();
      } catch {
        // Native module not yet linked (dev build without EAS)
      }

      // Hide splash once everything is bootstrapped
      setTimeout(() => SplashScreen.hideAsync().catch(() => {}), 400);
    }

    bootstrap();
  }, []);

  return (
    <GestureHandlerRootView style={styles.root}>
      <AppProvider>
        <AppNavigator />
        <StatusBar style="auto" />
      </AppProvider>
    </GestureHandlerRootView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
});
