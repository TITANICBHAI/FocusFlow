import type { RecurringBlockSchedule } from '@/data/types';

/**
 * Returns the VPN packages contributed by recurring schedules at a specific
 * local time. A non-empty vpnPackages list is authoritative; otherwise the
 * schedule's normal package list is used as the documented fallback.
 *
 * Calendar day values match Android Calendar.DAY_OF_WEEK:
 * 1 = Sunday ... 7 = Saturday.
 */
export function getActiveScheduleVpnPackages(
  schedules: RecurringBlockSchedule[],
  now: Date = new Date(),
): string[] {
  const nowDay = now.getDay() + 1;
  const previousDay = nowDay === 1 ? 7 : nowDay - 1;
  const nowMinutes = now.getHours() * 60 + now.getMinutes();
  const active = new Set<string>();

  for (const schedule of schedules) {
    if (!schedule.enabled || !schedule.vpnEnabled) continue;

    const packages = schedule.vpnPackages && schedule.vpnPackages.length > 0
      ? schedule.vpnPackages
      : schedule.packages;
    if (packages.length === 0) continue;

    const start = schedule.startHour * 60 + schedule.startMin;
    const end = schedule.endHour * 60 + schedule.endMin;
    const isActive = end > start
      ? schedule.days.includes(nowDay) && nowMinutes >= start && nowMinutes < end
      : end < start
        ? (schedule.days.includes(nowDay) && nowMinutes >= start) ||
          (schedule.days.includes(previousDay) && nowMinutes < end)
        : false;

    if (isActive) {
      packages.forEach((pkg) => {
        if (pkg.trim()) active.add(pkg);
      });
    }
  }

  return [...active].sort();
}