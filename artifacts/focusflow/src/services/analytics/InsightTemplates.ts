export function pickDeterministicVariant(
  variants: readonly string[],
  seed: number,
): string {
  if (variants.length === 0) return '';
  const index = Math.abs(Math.trunc(seed)) % variants.length;
  return variants[index];
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

export function weekSeed(generatedAt: string): number {
  const timestamp = Date.parse(generatedAt);
  return Number.isFinite(timestamp) ? Math.floor(timestamp / (7 * 24 * 60 * 60 * 1000)) : 0;
}