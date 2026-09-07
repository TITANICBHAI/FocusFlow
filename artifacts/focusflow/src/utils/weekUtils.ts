import dayjs, { type Dayjs } from 'dayjs';

/** Returns the most recent calendar day matching startDay (0=Sun … 6=Sat). */
export function getWeekStart(startDay = 0): Dayjs {
  const normalizedStartDay = ((startDay % 7) + 7) % 7;
  const today = dayjs();
  const diff = (today.day() - normalizedStartDay + 7) % 7;
  return today.subtract(diff, 'day').startOf('day');
}

export function getWeekEnd(weekStart: Dayjs): Dayjs {
  return weekStart.add(6, 'day').endOf('day');
}