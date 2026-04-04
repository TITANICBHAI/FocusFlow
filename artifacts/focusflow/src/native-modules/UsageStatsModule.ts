/**
 * Android UsageStats Native Module — Old Architecture (NativeModules bridge)
 *
 * Required permission: android.permission.PACKAGE_USAGE_STATS
 * User must manually grant in: Settings → Apps → Special app access → Usage access
 *
 * Kotlin: android-native/app/.../modules/UsageStatsModule.kt
 * Registered via: FocusDayPackage → createNativeModules()
 *
 * Methods exposed to JS:
 *   - getForegroundApp()                  → string | null
 *   - hasPermission()                     → boolean  (Usage Access granted)
 *   - openUsageAccessSettings()
 *   - hasAccessibilityPermission()        → boolean
 *   - openAccessibilitySettings()
 *   - isIgnoringBatteryOptimizations()    → boolean
 *   - openBatteryOptimizationSettings()
 *   - isDeviceAdminActive()               → boolean
 *   - openDeviceAdminSettings()
 */

import { NativeModules, Platform } from 'react-native';

const UsageStats = Platform.OS === 'android' ? NativeModules.UsageStats : null;

if (Platform.OS === 'android' && !UsageStats) {
  console.error('[UsageStatsModule] NativeModules.UsageStats not found. Ensure an EAS build is used — Expo Go does not include custom native modules.');
}

export const isUsageStatsAvailable = Platform.OS === 'android' && UsageStats != null;

export const UsageStatsModule = {
  async getForegroundApp(): Promise<string | null> {
    if (!UsageStats) return null;
    return UsageStats.getForegroundApp();
  },

  async hasPermission(): Promise<boolean> {
    if (!UsageStats) return false;
    return UsageStats.hasPermission();
  },

  async openUsageAccessSettings(): Promise<void> {
    if (!UsageStats) return;
    return UsageStats.openUsageAccessSettings();
  },

  async hasAccessibilityPermission(): Promise<boolean> {
    if (!UsageStats) return false;
    return UsageStats.hasAccessibilityPermission();
  },

  async openAccessibilitySettings(): Promise<void> {
    if (!UsageStats) return;
    return UsageStats.openAccessibilitySettings();
  },

  async isIgnoringBatteryOptimizations(): Promise<boolean> {
    if (!UsageStats) return false;
    return UsageStats.isIgnoringBatteryOptimizations();
  },

  async openBatteryOptimizationSettings(): Promise<void> {
    if (!UsageStats) return;
    return UsageStats.openBatteryOptimizationSettings();
  },

  async isDeviceAdminActive(): Promise<boolean> {
    if (!UsageStats) return false;
    return UsageStats.isDeviceAdminActive();
  },

  async openDeviceAdminSettings(): Promise<void> {
    if (!UsageStats) return;
    return UsageStats.openDeviceAdminSettings();
  },
};
