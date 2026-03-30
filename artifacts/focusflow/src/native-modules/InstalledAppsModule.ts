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

import { TurboModuleRegistry, TurboModule } from 'react-native';

export interface InstalledApp {
  packageName: string;
  appName: string;
  iconBase64?: string;
}

interface InstalledAppsSpec extends TurboModule {
  getInstalledApps(): Promise<InstalledApp[]>;
}

const InstalledApps = TurboModuleRegistry.get<InstalledAppsSpec>('InstalledApps');
console.log('[InstalledAppsModule] resolved:', !!InstalledApps);

export const InstalledAppsModule = {
  async getInstalledApps(): Promise<InstalledApp[]> {
    if (!InstalledApps) {
      console.error('[InstalledAppsModule] Native module "InstalledApps" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return [];
    }
    return InstalledApps.getInstalledApps();
  },
};
