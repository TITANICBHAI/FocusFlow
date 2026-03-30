/**
 * Android InstalledApps Native Module — Old Architecture (NativeModules bridge)
 *
 * Returns all apps visible in the device app drawer, including user-installed apps
 * and system apps that have a launcher icon. Uses getLaunchIntentForPackage() filter.
 *
 * Kotlin: android-native/app/.../modules/InstalledAppsModule.kt
 * Registered via: FocusDayPackage → createNativeModules()
 */

import { NativeModules, Platform } from 'react-native';

export interface InstalledApp {
  packageName: string;
  appName: string;
  iconBase64?: string;
}

const InstalledApps = Platform.OS === 'android' ? NativeModules.InstalledApps : null;

if (Platform.OS === 'android' && !InstalledApps) {
  console.error('[InstalledAppsModule] NativeModules.InstalledApps not found. Ensure an EAS build is used — Expo Go does not include custom native modules.');
}

export const InstalledAppsModule = {
  async getInstalledApps(): Promise<InstalledApp[]> {
    if (!InstalledApps) return [];
    return InstalledApps.getInstalledApps();
  },
};
