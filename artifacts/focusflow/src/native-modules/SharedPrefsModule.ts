import { TurboModuleRegistry, TurboModule } from 'react-native';

interface SharedPrefsSpec extends TurboModule {
  setFocusActive(active: boolean): Promise<void>;
  setAllowedPackages(packages: string[]): Promise<void>;
  setActiveTask(name: string, endMs: number, nextName: string | null): Promise<void>;
  setStandaloneBlock(active: boolean, packages: string[], untilMs: number): Promise<void>;
}

const SharedPrefs = TurboModuleRegistry.get<SharedPrefsSpec>('SharedPrefs');

/**
 * SharedPrefsModule
 *
 * Writes focus-mode and standalone-block state into Android SharedPreferences so:
 *   - AppBlockerAccessibilityService knows which apps to block even when JS is not running
 *   - BootReceiver can restart the foreground service after a reboot
 */
export const SharedPrefsModule = {
  async setFocusActive(active: boolean): Promise<void> {
    if (!SharedPrefs) {
      console.error('[SharedPrefsModule] Native module "SharedPrefs" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return SharedPrefs.setFocusActive(active);
  },

  /**
   * Write the list of ALLOWED Android package names (for task-based blocking).
   * The AccessibilityService blocks any foreground app NOT in this list during a task.
   */
  async setAllowedPackages(packages: string[]): Promise<void> {
    if (!SharedPrefs) {
      console.error('[SharedPrefsModule] Native module "SharedPrefs" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return SharedPrefs.setAllowedPackages(packages);
  },

  async setActiveTask(name: string, endMs: number, nextName: string | null): Promise<void> {
    if (!SharedPrefs) {
      console.error('[SharedPrefsModule] Native module "SharedPrefs" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return SharedPrefs.setActiveTask(name, endMs, nextName ?? null);
  },

  /**
   * Controls standalone app blocking — independent of any scheduled task.
   *
   * When active = true, the AccessibilityService blocks every package in the list
   * until untilMs is reached, even if no task focus session is running.
   *
   * Collision with task-based blocking: additive (both block lists enforced simultaneously).
   *
   * @param active    Whether standalone blocking is currently enabled
   * @param packages  Package names to block
   * @param untilMs   Epoch ms when standalone blocking expires (0 = no time limit)
   */
  async setStandaloneBlock(active: boolean, packages: string[], untilMs: number): Promise<void> {
    if (!SharedPrefs) {
      console.error('[SharedPrefsModule] Native module "SharedPrefs" not found. Ensure FocusDayPackage is registered and an EAS build was used.');
      return;
    }
    return SharedPrefs.setStandaloneBlock(active, packages, untilMs);
  },
};
