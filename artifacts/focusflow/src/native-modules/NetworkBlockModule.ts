/**
 * NetworkBlockModule — Old Architecture (NativeModules bridge)
 *
 * Controls the VPN-based network blocking layer that intercepts traffic from
 * blocked apps. When a blocked app is foregrounded the AccessibilityService
 * also disables Wi-Fi and mobile data as a belt-and-suspenders measure.
 *
 * stopNetworkBlock is a session-ending action — it requires a session PIN hash
 * if a PIN has been configured, otherwise native rejects the call silently.
 *
 * Kotlin: android-native/app/.../modules/NetworkBlockModule.kt
 * Registered via: FocusDayPackage → createNativeModules()
 */

import { NativeModules, Platform } from 'react-native';

const NetworkBlock = Platform.OS === 'android' ? NativeModules.NetworkBlock : null;

if (Platform.OS === 'android' && !NetworkBlock) {
  console.warn('[NetworkBlockModule] NativeModules.NetworkBlock not found. Network blocking is unavailable.');
}

function requireNetworkBlock(): Record<string, (...args: any[]) => Promise<any>> | null {
  if (Platform.OS !== 'android') return null;
  if (!NetworkBlock) {
    throw new Error(
      'FocusFlow native NetworkBlock module is missing. Build the Android app with the FocusFlow config plugin; Expo Go cannot provide this module.',
    );
  }
  return NetworkBlock as Record<string, (...args: any[]) => Promise<any>>;
}

export interface NetworkBlockStatus {
  state: string;
  running: boolean;
  error: string | null;
  failedPackages: string[];
}

export type NetworkBlockStartState =
  | 'starting'
  | 'running'
  | 'disabled'
  | 'permission_missing'
  | 'another_vpn_active'
  | 'package_registration_failed'
  | 'startup_failed';

export const NetworkBlockModule = {
  async startNetworkBlock(packagesJson: string): Promise<NetworkBlockStartState> {
    const native = requireNetworkBlock();
    if (!native) return 'disabled';
    if (typeof native.startNetworkBlock !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.startNetworkBlock is missing.');
    }
    return native.startNetworkBlock(packagesJson) as Promise<NetworkBlockStartState>;
  },

  /**
   * Tears down the VPN and re-enables Wi-Fi/mobile data.
   * If a session PIN is configured, pinHash must be the SHA-256 hex digest of
   * the PIN — otherwise native silently rejects the call.
   */
  async stopNetworkBlock(pinHash: string | null = null): Promise<void> {
    const native = requireNetworkBlock();
    if (!native) return;
    if (typeof native.stopNetworkBlock !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.stopNetworkBlock is missing.');
    }
    return native.stopNetworkBlock(pinHash);
  },

  async isNetworkBlockActive(): Promise<boolean> {
    const native = requireNetworkBlock();
    if (!native) return false;
    if (typeof native.isNetworkBlockActive !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.isNetworkBlockActive is missing.');
    }
    return native.isNetworkBlockActive();
  },

  /**
   * Returns true if the system VPN permission has already been granted.
   * VpnService.prepare() returns null when permission is held.
   */
  async isVpnPermissionGranted(): Promise<boolean> {
    const native = requireNetworkBlock();
    if (!native) return false;
    if (typeof native.isVpnPermissionGranted !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.isVpnPermissionGranted is missing.');
    }
    return native.isVpnPermissionGranted();
  },

  /**
   * Shows the system "FocusFlow wants to set up a VPN" consent dialog.
   * Must be called from a foregrounded activity — resolves immediately after
   * the dialog Intent is launched. Re-check isVpnPermissionGranted() after
   * the user returns to the app.
   */
  async requestVpnPermission(): Promise<void> {
    const native = requireNetworkBlock();
    if (!native) return;
    if (typeof native.requestVpnPermission !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.requestVpnPermission is missing.');
    }
    return native.requestVpnPermission();
  },

  /**
   * Returns true if a VPN from a different app is currently active on the device.
   * FocusFlow's own VPN tunnel is excluded — if our service is the active VPN
   * this returns false (no conflict).
   *
   * Use this before enabling VPN blocking to detect a work/privacy VPN that
   * would be replaced and warn the user before Android silently kicks it out.
   */
  async isAnotherVpnActive(): Promise<boolean> {
    const native = requireNetworkBlock();
    if (!native) return false;
    if (typeof native.isAnotherVpnActive !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.isAnotherVpnActive is missing.');
    }
    return native.isAnotherVpnActive();
  },

  async setNetworkBlockSettings(settings: {
    enabled: boolean;
    vpn: boolean;
    packages: string[];
  }): Promise<void> {
    const native = requireNetworkBlock();
    if (!native) return;
    if (typeof native.setNetworkBlockSettings !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.setNetworkBlockSettings is missing.');
    }
    await native.setNetworkBlockSettings(JSON.stringify({
      enabled: settings.enabled,
      vpn: settings.vpn,
      packages: JSON.stringify(Array.from(new Set(settings.packages))),
    }));
  },

  async getNetworkBlockStatus(): Promise<NetworkBlockStatus> {
    const native = requireNetworkBlock();
    if (!native) {
      return { state: 'disabled', running: false, error: null, failedPackages: [] };
    }
    if (typeof native.getNetworkBlockStatus !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.getNetworkBlockStatus is missing.');
    }
    const raw = await native.getNetworkBlockStatus();
    const parsed = JSON.parse(String(raw)) as {
      state?: string;
      running?: boolean;
      error?: string | null;
      failedPackages?: string | string[];
    };
    let failedPackages: string[] = [];
    if (Array.isArray(parsed.failedPackages)) {
      failedPackages = parsed.failedPackages;
    } else if (typeof parsed.failedPackages === 'string') {
      try {
        const value = JSON.parse(parsed.failedPackages);
        if (Array.isArray(value)) failedPackages = value.filter((p): p is string => typeof p === 'string');
      } catch { /* native status remains useful without the package detail */ }
    }
    return {
      state: parsed.state ?? 'stopped',
      running: Boolean(parsed.running),
      error: parsed.error ?? null,
      failedPackages,
    };
  },

  /**
   * Persists the VPN self-heal preference to SharedPrefs.
   *
   * When enabled, two complementary mechanisms keep the VPN alive mid-session:
   *   1. NetworkBlockerVpnService.onRevoke() schedules a restart (3 s delay).
   *   2. AppBlockerAccessibilityService runs a 10-second health-check loop.
   *
   * Both read the "net_block_self_heal" SharedPrefs key set by this call.
   */
  async setVpnSelfHealEnabled(enabled: boolean): Promise<void> {
    const native = requireNetworkBlock();
    if (!native) return;
    if (typeof native.setVpnSelfHealEnabled !== 'function') {
      throw new Error('FocusFlow native NetworkBlock.setVpnSelfHealEnabled is missing.');
    }
    return native.setVpnSelfHealEnabled(enabled);
  },
};
