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
 *   - getUsageSummary(startMs, endMs)     → UsageSummary | null
 *   - getHourlyUsageSummary(startMs, endMs) → UsageHourlySummary | null
 *   - hasPermission()                     → boolean  (Usage Access granted)
 *   - openUsageAccessSettings()
 *   - hasAccessibilityPermission()        → boolean
 *   - openAccessibilitySettings()
 *   - isIgnoringBatteryOptimizations()    → boolean
 *   - openBatteryOptimizationSettings()
 *   - isDeviceAdminActive()               → boolean
 *   - openDeviceAdminSettings()
 *   - isRestrictedSettingsBlocked()       → boolean   (Android 13+ sideload wall)
 *   - openAppInfoSettings()                          (App info page → ⋮ menu)
 *   - getInstallerPackage()               → string | null
 */

import { NativeModules, Platform } from 'react-native';

const UsageStats = Platform.OS === 'android' ? NativeModules.UsageStats : null;

if (Platform.OS === 'android' && !UsageStats) {
  console.error('[UsageStatsModule] NativeModules.UsageStats not found. Ensure an EAS build is used — Expo Go does not include custom native modules.');
}

export const isUsageStatsAvailable = Platform.OS === 'android' && UsageStats != null;

export interface UsageApp {
  packageName: string;
  appName?: string;
  foregroundMinutes: number;
  launchCount: number;
  lastUsedAt: number;
}

export interface UsageSummary {
  totalMinutes: number;
  apps: UsageApp[];
}

/**
 * Raw hourly foreground time. The native side deliberately keeps these values
 * in milliseconds so the processor can aggregate before converting to display
 * minutes, avoiding per-bucket flooring loss.
 */
export interface UsageHourlySummary {
  foregroundMillisecondsByHour: number[];
  totalForegroundMilliseconds: number;
}

export const isUsageSummaryAvailable =
  Platform.OS === 'android' && typeof UsageStats?.getUsageSummary === 'function';

export const isUsageHourlySummaryAvailable =
  Platform.OS === 'android' && typeof UsageStats?.getHourlyUsageSummary === 'function';

export const UsageStatsModule = {
  async getForegroundApp(): Promise<string | null> {
    if (!UsageStats) return null;
    return UsageStats.getForegroundApp();
  },

  async getUsageSummary(startMs: number, endMs: number): Promise<UsageSummary | null> {
    if (!UsageStats?.getUsageSummary) return null;
    return UsageStats.getUsageSummary(startMs, endMs) as Promise<UsageSummary>;
  },

  async getHourlyUsageSummary(
    startMs: number,
    endMs: number,
  ): Promise<UsageHourlySummary | null> {
    if (!UsageStats?.getHourlyUsageSummary) return null;
    return UsageStats.getHourlyUsageSummary(startMs, endMs) as Promise<UsageHourlySummary>;
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

  /**
   * Android 13+ "Restricted Settings" detector.
   *
   * Returns true when the OS is currently blocking the user from toggling
   * sensitive permissions (Accessibility, Device Admin) because FocusFlow
   * was installed from a non-Play-Store source. When true, the user must do:
   *
   *   App info → ⋮ menu (top-right) → "Allow restricted settings"
   *
   * before the Accessibility toggle becomes tappable.
   *
   * Returns false on Android < 13 (the wall didn't exist), on Play Store
   * installs (the OS auto-allows), and on installs from trusted OEM stores
   * (Galaxy Store, Oppo Market, Xiaomi GetApps, Vivo App Store, AppGallery).
   */
  async isRestrictedSettingsBlocked(): Promise<boolean> {
    if (!UsageStats) return false;
    try {
      return await UsageStats.isRestrictedSettingsBlocked();
    } catch {
      return false;
    }
  },

  /**
   * Opens this app's App Info screen — the screen that contains the
   * ⋮ (three-dot) overflow menu where "Allow restricted settings" lives.
   * Used to walk the user through the Android 13+ sideload unlock flow.
   */
  async openAppInfoSettings(): Promise<void> {
    if (!UsageStats) return;
    return UsageStats.openAppInfoSettings();
  },

  /**
   * Returns the package name of the app store / installer that placed
   * FocusFlow on this device, or null if unknown. Used to show targeted
   * unlock guidance (Play Store → no unlock needed, Aptoide → unlock needed,
   * sideload via APK → unlock needed, etc.).
   */
  async getInstallerPackage(): Promise<string | null> {
    if (!UsageStats) return null;
    try {
      return await UsageStats.getInstallerPackage();
    } catch {
      return null;
    }
  },
};
