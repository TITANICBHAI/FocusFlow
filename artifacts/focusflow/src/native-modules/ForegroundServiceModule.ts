/**
 * Android Foreground Service Module — Old Architecture (NativeModules bridge)
 *
 * The ForegroundTaskService runs PERSISTENTLY at all times — not only during focus.
 * This keeps the process alive so Android cannot kill the AccessibilityService.
 *
 * Modes:
 *   IDLE   — Quiet "FocusFlow is monitoring" notification shown at all times.
 *   ACTIVE — Focus session running: shows task name + live countdown.
 *
 * Kotlin: android-native/app/.../services/ForegroundTaskService.kt
 * Registered via: FocusDayPackage → createNativeModules()
 */

import { NativeModules, Platform } from 'react-native';

const ForegroundService = Platform.OS === 'android' ? NativeModules.ForegroundService : null;

if (Platform.OS === 'android' && !ForegroundService) {
  console.error('[ForegroundServiceModule] NativeModules.ForegroundService not found. Ensure an EAS build is used — Expo Go does not include custom native modules.');
}

export const ForegroundServiceModule = {
  async startIdleService(): Promise<void> {
    if (!ForegroundService) return;
    return ForegroundService.startIdleService();
  },

  async startService(taskName: string, endTimeMs: number, nextTaskName: string | null): Promise<void> {
    if (!ForegroundService) return;
    return ForegroundService.startService(taskName, endTimeMs, nextTaskName);
  },

  async stopService(): Promise<void> {
    if (!ForegroundService) return;
    return ForegroundService.stopService();
  },

  async updateNotification(taskName: string, endTimeMs: number, nextTaskName: string | null): Promise<void> {
    if (!ForegroundService) return;
    return ForegroundService.updateNotification(taskName, endTimeMs, nextTaskName);
  },

  async requestBatteryOptimizationExemption(): Promise<void> {
    if (!ForegroundService) return;
    return ForegroundService.requestBatteryOptimizationExemption();
  },
};
