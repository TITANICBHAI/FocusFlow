/**
 * Android Foreground Launch Module
 *
 * Provides app-switching utilities used during focus mode.
 *
 * ─── Kotlin Implementation ────────────────────────────────────────────────────
 * File: android-native/app/.../modules/ForegroundLaunchModule.kt
 *
 * Methods:
 *   - goHome()                      — send device to home screen (no permission needed)
 *   - bringToFront()                — re-launch FocusFlow over blocked app
 *   - showOverlay(message: String)  — placeholder for future full-screen overlay
 *   - hasOverlayPermission()        — returns true if SYSTEM_ALERT_WINDOW is granted
 *   - requestOverlayPermission()    — opens system settings for SYSTEM_ALERT_WINDOW
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * DEFERRED: Full-screen lock overlay
 * See ForegroundLaunchModule.kt for the implementation plan (requires
 * a FocusLockActivity + USE_FULL_SCREEN_INTENT or SYSTEM_ALERT_WINDOW).
 * ──────────────────────────────────────────────────────────────────────────────
 */

import { TurboModuleRegistry, TurboModule } from 'react-native';

interface ForegroundLaunchSpec extends TurboModule {
  goHome(): Promise<void>;
  bringToFront(): Promise<void>;
  showOverlay(message: string): Promise<void>;
  hasOverlayPermission(): Promise<boolean>;
  requestOverlayPermission(): Promise<void>;
}

const ForegroundLaunch = TurboModuleRegistry.get<ForegroundLaunchSpec>('ForegroundLaunch');

export const ForegroundLaunchModule = {
  /**
   * Sends the device to the home screen.
   * Called after Activate Focus so the user lands on their home screen
   * while FocusFlow continues enforcing in the background.
   */
  async goHome(): Promise<void> {
    if (!ForegroundLaunch) {
      console.error('[ForegroundLaunchModule] Native module "ForegroundLaunch" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundLaunch.goHome();
  },

  async bringToFront(): Promise<void> {
    if (!ForegroundLaunch) {
      console.error('[ForegroundLaunchModule] Native module "ForegroundLaunch" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundLaunch.bringToFront();
  },

  async showOverlay(message: string): Promise<void> {
    if (!ForegroundLaunch) {
      console.error('[ForegroundLaunchModule] Native module "ForegroundLaunch" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundLaunch.showOverlay(message);
  },

  async hasOverlayPermission(): Promise<boolean> {
    if (!ForegroundLaunch) {
      console.error('[ForegroundLaunchModule] Native module "ForegroundLaunch" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return false;
    }
    return ForegroundLaunch.hasOverlayPermission();
  },

  async requestOverlayPermission(): Promise<void> {
    if (!ForegroundLaunch) {
      console.error('[ForegroundLaunchModule] Native module "ForegroundLaunch" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return ForegroundLaunch.requestOverlayPermission();
  },
};
