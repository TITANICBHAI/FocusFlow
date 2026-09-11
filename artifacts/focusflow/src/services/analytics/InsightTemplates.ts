type TemplateValue = string | number;

export const INSIGHT_VARIANTS = {
  YESTERDAY_PERFECT_DAY: [
    'Clean day. You finished everything and never reached for a blocked app.',
    'Nothing to flag today. Every task done, zero blocked-app attempts.',
  ],
  YESTERDAY_CLEAN_NO_BLOCKS: [
    'You never reached for a blocked app today. That part was clean.',
  ],
  YESTERDAY_PEAK_BLOCK_HOUR: [
    'You resisted {count} blocked-app attempts. {peak_hour_label} was your hardest hour, with {peak_count} of them.',
    '{count} times you reached for something blocked. Most of those — {peak_count} — happened {peak_hour_label}.',
  ],
  YESTERDAY_SECOND_SKIP_THIS_WEEK: [
    "You've skipped {count} tasks this week. Not a problem if it's deliberate.",
    "{count} skips this week. If the schedule isn't working, it's worth adjusting rather than skipping.",
  ],
  YESTERDAY_SINGLE_HARD_SESSION: [
    'Your {session_time} session had {count} blocked-app attempts. That one was hard.',
  ],
  YESTERDAY_NOTHING_NOTABLE: [
    'Ordinary day. You showed up, you worked, nothing unusual happened.',
  ],
  WEEKLY_VULNERABLE_WINDOW: [
    '{peak_hour_label} is your weakest hour this week. {peak_count} of your {total} blocked-app attempts happened then.',
    'Between {peak_hour_start} and {peak_hour_end}, you reached for a blocked app {peak_count} times — more than any other hour.',
  ],
  WEEKLY_CLEAN_WINDOW: [
    '{clean_window} was consistently quiet this week. No blocked-app attempts during that window.',
  ],
  WEEKLY_ONE_APP: [
    "{app_name} made up {share}% of your blocked-app attempts this week. It's not a habit — it's the habit.",
    'More than half your blocked-app attempts were {app_name}. {share}% to be exact.',
  ],
  WEEKLY_TOP_APP_MODERATE: [
    '{app_name} was your most blocked app this week — {count} attempts.',
  ],
  WEEKLY_ESTIMATION_IMPROVING: [
    'Your task estimates were solid this week. You were off by less than 10 minutes on average.',
  ],
  WEEKLY_UNDERESTIMATING: [
    'Your tasks are consistently taking longer than you schedule. {avg_error} minutes over on average.',
    "You're underestimating. Tasks ran {avg_error} minutes over their scheduled time on average this week.",
  ],
  WEEKLY_OVERESTIMATING: [
    "You're finishing tasks faster than you schedule them — {abs_error} minutes ahead on average. Your buffers might be too generous.",
  ],
  WEEKLY_SHOWED_UP: [
    'You showed up {count} of 7 days.',
    "Every day this week. That's the whole job.",
    'You showed up {count} of 7 days this week.',
  ],
  WEEKLY_BETTER_THAN_LAST: [
    'Better week than last. Completion rate up, blocking attempts {direction}.',
    "This week was cleaner than last. You're moving in the right direction.",
  ],
  WEEKLY_WORSE_THAN_LAST: [
    'Harder week than last. Completion rate dropped, more blocking attempts.',
    'This week was messier than last. Worth paying attention to.',
  ],
  WEEKLY_FLAT: [
    'Consistent with last week. Not better, not worse.',
  ],
  WEEKLY_NOTHING_UNUSUAL: [
    'Nothing unusual this week. You showed up, you finished things, nothing spiked. Sometimes the analysis is: you did well.',
  ],
  THREE_MONTH_PHONE_PEAK: [
    'Your heaviest phone use is consistently between {peak_start} and {peak_end}. Every week, without exception.',
    "{peak_period_label} is when you use your phone most. That's been true across all 12 weeks.",
  ],
  THREE_MONTH_REAL_FOCUS_WINDOW: [
    "Tasks you start around {hour_label} consistently finish faster than your estimates. That's your sharpest window.",
    'Your best work happens {hour_label}. Tasks started then run {pct}% closer to your planned time than any other slot.',
  ],
  THREE_MONTH_SCHEDULING_HONESTY: [
    "You've scheduled tasks on {day_name} for 12 weeks. Your completion rate that day is {rate}%. Consider whether {day_name} is actually available to you.",
    "{day_name} is your most scheduled day. It's also your worst completion day, at {rate}%. Something doesn't add up.",
  ],
  THREE_MONTH_REAL_PROBLEM_APP: [
    "In 12 weeks, {app_name} has accounted for {share}% of every blocked-app attempt. That's not a coincidence. That's the thing.",
    '{app_name} is responsible for {share}% of your blocked attempts over 3 months. One app.',
  ],
  THREE_MONTH_IMPROVING: [
    "Your task completion rate has improved over the last 3 months. You're actually getting better at this.",
    'Slow improvement across 12 weeks. Completion rate is up {delta}pt from where you started.',
  ],
  THREE_MONTH_PLATEAU: [
    "You've been at around {rate}% completion for 6 weeks. You've hit a ceiling at your current setup.",
    "Flat for 6 weeks. Not getting worse, but not improving. Something about the routine isn't working.",
  ],
  THREE_MONTH_NIGHT_PATTERN: [
    'After 9pm, your phone use is consistently {mult}x higher than during the day. Every week.',
    'Your phone use triples after 9pm. That pattern has held across all 12 weeks.',
  ],
  THREE_MONTH_INSUFFICIENT_DATA: [
    'Not enough data yet for 3-month patterns. Come back after {weeks_remaining} more weeks.',
  ],
} as const;

export function pickDeterministicVariant(
  variants: readonly string[],
  seed: number,
): string {
  if (variants.length === 0) return '';
  const index = Math.abs(Math.trunc(seed)) % variants.length;
  return variants[index];
}

export function renderInsightVariant(
  id: keyof typeof INSIGHT_VARIANTS,
  seed: number,
  values: Record<string, TemplateValue> = {},
): string {
  const template = pickDeterministicVariant(INSIGHT_VARIANTS[id], seed);
  return template.replace(/\{([a-z_]+)\}/g, (match, key: string) =>
    Object.prototype.hasOwnProperty.call(values, key) ? String(values[key]) : match,
  );
}

export function hourLabel(hour: number | null): string {
  if (hour === null || hour < 0 || hour > 23) return 'that hour';
  const normalized = hour % 12 || 12;
  return `${normalized}${hour < 12 ? 'am' : 'pm'}`;
}

export function hourWindowLabel(hour: number | null): string {
  if (hour === null || hour < 0 || hour > 23) return 'that window';
  return `${hourLabel(hour)}–${hourLabel((hour + 1) % 24)}`;
}

export function twoHourWindowLabel(hour: number | null): string {
  if (hour === null || hour < 0 || hour > 23) return 'that window';
  return `${hourLabel(hour)}–${hourLabel((hour + 2) % 24)}`;
}

export function taskResultLine(
  title: string,
  status: 'completed' | 'skipped' | 'overdue' | 'scheduled' | 'active',
): string {
  const symbol = status === 'completed' ? '✓' : status === 'skipped' ? '—' : status === 'overdue' ? '✗' : '·';
  return `${symbol} ${title}`;
}

export function weekSeed(generatedAt: string): number {
  const timestamp = Date.parse(generatedAt);
  return Number.isFinite(timestamp) ? Math.floor(timestamp / (7 * 24 * 60 * 60 * 1000)) : 0;
}