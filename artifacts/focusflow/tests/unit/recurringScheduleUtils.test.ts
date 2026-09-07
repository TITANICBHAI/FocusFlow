import { describe, expect, it } from 'vitest';
import type { RecurringBlockSchedule } from '@/data/types';
import { getActiveScheduleVpnPackages } from '@/utils/recurringScheduleUtils';

function schedule(overrides: Partial<RecurringBlockSchedule> = {}): RecurringBlockSchedule {
  return {
    id: 'schedule-1',
    name: 'Work block',
    packages: ['com.example.overlay'],
    days: [2, 3, 4, 5, 6],
    startHour: 9,
    startMin: 0,
    endHour: 18,
    endMin: 0,
    enabled: true,
    vpnEnabled: true,
    ...overrides,
  };
}

describe('recurring schedule VPN packages', () => {
  it('uses explicit VPN packages instead of overlay packages', () => {
    const now = new Date(2026, 8, 8, 10, 0);
    expect(getActiveScheduleVpnPackages([
      schedule({ vpnPackages: ['com.example.network-only'] }),
    ], now)).toEqual(['com.example.network-only']);
  });

  it('falls back to the schedule packages when no VPN list is selected', () => {
    const now = new Date(2026, 8, 8, 10, 0);
    expect(getActiveScheduleVpnPackages([schedule()], now)).toEqual(['com.example.overlay']);
  });

  it('supports an overnight schedule across its configured day boundary', () => {
    const overnight = schedule({
      days: [2],
      startHour: 22,
      endHour: 2,
      vpnPackages: ['com.example.night'],
    });

    expect(getActiveScheduleVpnPackages([overnight], new Date(2026, 8, 7, 23, 0)))
      .toEqual(['com.example.night']);
    expect(getActiveScheduleVpnPackages([overnight], new Date(2026, 8, 8, 1, 0)))
      .toEqual(['com.example.night']);
    expect(getActiveScheduleVpnPackages([overnight], new Date(2026, 8, 8, 3, 0)))
      .toEqual([]);
  });

  it('does not activate disabled schedules or empty package lists', () => {
    const now = new Date(2026, 8, 8, 10, 0);
    expect(getActiveScheduleVpnPackages([
      schedule({ enabled: false }),
      schedule({ packages: [], vpnPackages: [] }),
    ], now)).toEqual([]);
  });
});