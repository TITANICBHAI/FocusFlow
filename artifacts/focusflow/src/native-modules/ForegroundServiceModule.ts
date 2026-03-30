/**
 * Android Foreground Service Module
 *
 * The ForegroundTaskService runs PERSISTENTLY at all times — not only during focus.
 * This keeps the process alive so Android cannot kill the AccessibilityService.
 *
 * Modes:
 *   IDLE   — Quiet "FocusFlow is monitoring" notification shown at all times.
 *   ACTIVE — Focus session running: shows task name + live countdown.
 *
 * ─── Android Implementation ──────────────────────────────────────────────────
 * File: android-native/app/.../services/ForegroundTaskService.kt
 */

import { TurboModuleRegistry, TurboModule } from 'react-native';

interface ForegroundServiceSpec extends TurboModule {
  startIdleService(): Promise<void>;
  startService(taskName: string, endTimeMs: number, nextTaskName: string | null): Promise<void>;
  stopService(): Promise<void>;
  updateNotification(taskName: string, endTimeMs: number, nextTaskName: string | null): Promise<void>;
  requestBatteryOptimizationExemption(): Promise<void>;
}

const ForegroundService = TurboModuleRegistry.get<ForegroundServiceSpec>('ForegroundService');

export const ForegroundServiceModule = {
  /**
   * Ensures the foreground service is running in idle mode.
   * Call on every app startup to guarantee the persistent notification always exists.
   * Safe to call if the service is already running in active mode — it will stay active.
   */
  async startIdleService(): Promise<void> {
    if (!ForegroundService) {
      console.error('[ForegroundServiceModule] Native module "ForegroundService" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundService.startIdleService();
  },

  async startService(taskName: string, endTimeMs: number, nextTaskName: string | null): Promise<void> {
    if (!ForegroundService) {
      console.error('[ForegroundServiceModule] Native module "ForegroundService" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundService.startService(taskName, endTimeMs, nextTaskName);
  },

  /**
   * Switches the service to idle mode (persistent notification stays, countdown stops).
   * Does NOT stop the service — it remains alive to keep the process running.
   */
  async stopService(): Promise<void> {
    if (!ForegroundService) {
      console.error('[ForegroundServiceModule] Native module "ForegroundService" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundService.stopService();
  },

  async updateNotification(taskName: string, endTimeMs: number, nextTaskName: string | null): Promise<void> {
    if (!ForegroundService) {
      console.error('[ForegroundServiceModule] Native module "ForegroundService" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundService.updateNotification(taskName, endTimeMs, nextTaskName);
  },

  async requestBatteryOptimizationExemption(): Promise<void> {
    if (!ForegroundService) {
      console.error('[ForegroundServiceModule] Native module "ForegroundService" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundService.requestBatteryOptimizationExemption();
  },
};
