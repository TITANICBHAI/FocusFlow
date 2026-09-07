import dayjs, { type Dayjs } from 'dayjs';

/** Returns the most recent calendar day matching startDay (0=Sun … 6=Sat). */
export function getWeekStart(startDay = 0, referenceDate: Dayjs = dayjs()): Dayjs {
  const normalizedStartDay = ((startDay % 7) + 7) % 7;
  const diff = (referenceDate.day() - normalizedStartDay + 7) % 7;
  return referenceDate.subtract(diff, 'day').startOf('day');
}

export function getWeekEnd(weekStart: Dayjs): Dayjs {
  return weekStart.add(6, 'day').endOf('day');
}