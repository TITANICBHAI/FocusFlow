/**
 * Android InstalledApps Native Module
 *
 * Returns all user-installed apps (system apps excluded).
 *
 * ─── Kotlin Implementation ────────────────────────────────────────────────────
 * File: android-native/app/src/main/java/com/tbtechs/focusflow/modules/InstalledAppsModule.kt
 *
 * Exposes one method to JS:
 *   - getInstalledApps(): Array<InstalledApp>
 */

import { NativeModules } from 'react-native';

export interface InstalledApp {
  packageName: string;
  appName: string;
  iconBase64?: string;
}

const { InstalledApps } = NativeModules;

export const InstalledAppsModule = {
  async getInstalledApps(): Promise<InstalledApp[]> {
    if (!InstalledApps) {
      console.warn('[InstalledAppsModule] Native module not linked. Run EAS build.');
      return [];
    }
    return InstalledApps.getInstalledApps();
  },
};
