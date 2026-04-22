/**
 * SharedPrefsModule — Old Architecture (NativeModules bridge)
 *
 * Writes focus-mode and standalone-block state into Android SharedPreferences so:
 *   - AppBlockerAccessibilityService knows which apps to block even when JS is not running
 *   - BootReceiver can restore the foreground service state after device reboot
 *
 * Kotlin: android-native/app/.../modules/SharedPrefsModule.kt
 * Registered via: FocusDayPackage → createNativeModules()
 */

import { NativeModules, Platform } from 'react-native';
import type { DailyAllowanceEntry, CustomNodeRule } from '@/data/types';

const SharedPrefs = Platform.OS === 'android' ? NativeModules.SharedPrefs : null;

if (Platform.OS === 'android' && !SharedPrefs) {
  console.error('[SharedPrefsModule] NativeModules.SharedPrefs not found. Ensure an EAS build is used — Expo Go does not include custom native modules.');
}

export const isSharedPrefsAvailable = Platform.OS === 'android' && SharedPrefs != null;

function hasSharedPrefsMethod(name: string): boolean {
  return !!SharedPrefs && typeof SharedPrefs[name] === 'function';
}

export const SharedPrefsModule = {
  /**
   * Tells native whether task focus is active.
   * When deactivating (active=false) and a session PIN is configured, pinHash
   * must be the SHA-256 hex digest of the PIN — otherwise the call is rejected.
   */
  async setFocusActive(active: boolean, pinHash: string | null = null): Promise<void> {
    if (!hasSharedPrefsMethod('setFocusActive')) return;
    return SharedPrefs.setFocusActive(active, pinHash);
  },

  async setAllowedPackages(packages: string[]): Promise<void> {
    if (!hasSharedPrefsMethod('setAllowedPackages')) return;
    return SharedPrefs.setAllowedPackages(packages);
  },

  async setActiveTask(taskId: string, name: string, endMs: number, nextName: string | null): Promise<void> {
    if (!hasSharedPrefsMethod('setActiveTask')) return;
    return SharedPrefs.setActiveTask(taskId, name, endMs, nextName ?? null);
  },

  /**
   * Writes the active task's accent color (hex string, e.g. "#6366f1") so the
   * widget can tint its header / sub-line. Pass an empty string to clear.
   * Triggers a widget redraw on the native side.
   */
  async setActiveTaskColor(colorHex: string): Promise<void> {
    if (!hasSharedPrefsMethod('setActiveTaskColor')) return;
    return SharedPrefs.setActiveTaskColor(colorHex ?? '');
  },

  /**
   * Persists the wall-clock start time of the active task so the widget can
   * draw a progress bar even when the session was not started by the focus
   * service. Idempotent — only writes when task identity changes.
   * Pass 0 / negative to clear.
   */
  async setActiveTaskStartMs(taskId: string, startMs: number): Promise<void> {
    if (!hasSharedPrefsMethod('setActiveTaskStartMs')) return;
    return SharedPrefs.setActiveTaskStartMs(taskId, startMs);
  },

  /**
   * Clears the active-task fields in SharedPreferences (without touching focus
   * or block state) and pushes a widget redraw.
   */
  async clearActiveTask(): Promise<void> {
    if (!hasSharedPrefsMethod('clearActiveTask')) return;
    return SharedPrefs.clearActiveTask();
  },

  /**
   * Forces a redraw of any home-screen widgets using whatever is currently in
   * SharedPreferences. Use after standalone-block / task state changes that
   * happen outside a focus session (where ForegroundTaskService would have
   * pushed automatically).
   */
  async pushWidgetUpdate(): Promise<void> {
    if (!hasSharedPrefsMethod('pushWidgetUpdate')) return;
    return SharedPrefs.pushWidgetUpdate();
  },

  async setStandaloneBlock(active: boolean, packages: string[], untilMs: number): Promise<void> {
    if (!hasSharedPrefsMethod('setStandaloneBlock')) return;
    return SharedPrefs.setStandaloneBlock(active, packages, untilMs);
  },

  /**
   * Writes the rich daily allowance config (DailyAllowanceEntry[]) to SharedPreferences
   * as a JSON string. The AccessibilityService reads this to enforce per-app allowance modes.
   *
   * Replaces the old setDailyAllowancePackages (string[]) method.
   */
  async setDailyAllowanceConfig(entries: DailyAllowanceEntry[]): Promise<void> {
    if (!hasSharedPrefsMethod('setDailyAllowanceConfig')) return;
    return SharedPrefs.setDailyAllowanceConfig(JSON.stringify(entries));
  },

  async resetDailyAllowanceUsage(packageName: string | null): Promise<void> {
    if (!hasSharedPrefsMethod('resetDailyAllowanceUsage')) return;
    return SharedPrefs.resetDailyAllowanceUsage(packageName ?? null);
  },

  async setBlockedWords(words: string[]): Promise<void> {
    if (!hasSharedPrefsMethod('setBlockedWords')) return;
    return SharedPrefs.setBlockedWords(words);
  },

  async setSystemGuardEnabled(enabled: boolean): Promise<void> {
    if (!hasSharedPrefsMethod('setSystemGuardEnabled')) return;
    return SharedPrefs.setSystemGuardEnabled(enabled);
  },

  async putString(key: string, value: string): Promise<void> {
    if (!hasSharedPrefsMethod('putString')) return;
    return SharedPrefs.putString(key, value);
  },

  /**
   * Writes the custom node rules (imported from NodeSpy NodeSpyCaptureV1 exports)
   * to SharedPreferences as a JSON string.
   * The AccessibilityService reads this to enforce per-node blocking rules.
   *
   * Only enabled rules are passed — disabled rules are filtered out client-side
   * before calling this method to keep the native scan loop fast.
   */
  async setCustomNodeRules(rules: CustomNodeRule[]): Promise<void> {
    if (!hasSharedPrefsMethod('setCustomNodeRules')) return;
    const enabledRules = rules.filter(r => r.enabled);
    return SharedPrefs.setCustomNodeRules(JSON.stringify(enabledRules));
  },
};
