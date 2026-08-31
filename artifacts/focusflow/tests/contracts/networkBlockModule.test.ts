import { beforeEach, describe, expect, it, vi } from 'vitest';

const { nativeNetworkBlock } = vi.hoisted(() => ({
  nativeNetworkBlock: {
    startNetworkBlock: vi.fn(),
    stopNetworkBlock: vi.fn(),
    isNetworkBlockActive: vi.fn(),
    isVpnPermissionGranted: vi.fn(),
    requestVpnPermission: vi.fn(),
    isAnotherVpnActive: vi.fn(),
    setNetworkBlockSettings: vi.fn(),
    reconcileVpnPolicy: vi.fn(),
    getNetworkBlockStatus: vi.fn(),
    setVpnSelfHealEnabled: vi.fn(),
  },
}));

vi.mock('react-native', () => ({
  AppState: { addEventListener: vi.fn() },
  NativeModules: { NetworkBlock: nativeNetworkBlock },
  Platform: { OS: 'android' },
}));

import { NetworkBlockModule } from '@/native-modules/NetworkBlockModule';

describe('NetworkBlock JS↔native contract', () => {
  beforeEach(() => {
    for (const mock of Object.values(nativeNetworkBlock)) {
      mock.mockReset();
      mock.mockResolvedValue(undefined);
    }
  });

  it('serializes VPN settings as a deduplicated package array', async () => {
    await NetworkBlockModule.setNetworkBlockSettings({
      enabled: true,
      vpn: true,
      packages: ['com.example.video', 'com.example.video', 'com.example.social'],
    });

    expect(nativeNetworkBlock.setNetworkBlockSettings).toHaveBeenCalledWith(
      JSON.stringify({
        enabled: true,
        vpn: true,
        packages: JSON.stringify(['com.example.video', 'com.example.social']),
      }),
    );
  });

  it('awaits the native policy reconciliation call', async () => {
    await NetworkBlockModule.reconcileVpnPolicy();
    expect(nativeNetworkBlock.reconcileVpnPolicy).toHaveBeenCalledOnce();
  });

  it('awaits the native self-healing preference write', async () => {
    await NetworkBlockModule.setVpnSelfHealEnabled(true);
    expect(nativeNetworkBlock.setVpnSelfHealEnabled).toHaveBeenCalledWith(true);
  });

  it('normalizes native status package payloads and preserves diagnostics', async () => {
    nativeNetworkBlock.getNetworkBlockStatus.mockResolvedValue(JSON.stringify({
      state: 'package_registration_failed',
      running: true,
      error: 'one package failed',
      failedPackages: JSON.stringify(['com.example.missing']),
      desiredGeneration: 8,
      appliedGeneration: 7,
      recoveryPending: true,
    }));

    await expect(NetworkBlockModule.getNetworkBlockStatus()).resolves.toEqual({
      state: 'package_registration_failed',
      running: true,
      error: 'one package failed',
      failedPackages: ['com.example.missing'],
      desiredGeneration: 8,
      appliedGeneration: 7,
      recoveryPending: true,
    });
  });
});