/**
 * Human-readable elapsed time ("3 hours ago", "il y a 2 jours", "قبل ساعتين") in the given locale, for "assigned
 * since" and "waiting since" texts.
 */
export function elapsedText(fromIso: string, now: number, locale: string): string {
  const from = Date.parse(fromIso);
  if (!Number.isFinite(from)) {
    return '';
  }
  const seconds = Math.max(0, Math.round((now - from) / 1000));
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ];
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto', style: 'long' });
  for (const [unit, size] of units) {
    if (seconds >= size) {
      return format.format(-Math.floor(seconds / size), unit);
    }
  }
  return format.format(0, 'minute');
}

/** Time remaining until a due date ("in 3 hours"), or elapsed when already past. */
export function untilText(toIso: string, now: number, locale: string): string {
  const to = Date.parse(toIso);
  if (!Number.isFinite(to)) {
    return '';
  }
  const seconds = Math.round((to - now) / 1000);
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto', style: 'long' });
  const absolute = Math.abs(seconds);
  const sign = Math.sign(seconds) || 1;
  if (absolute >= 86400) {
    return format.format(sign * Math.floor(absolute / 86400), 'day');
  }
  if (absolute >= 3600) {
    return format.format(sign * Math.floor(absolute / 3600), 'hour');
  }
  return format.format(sign * Math.max(1, Math.floor(absolute / 60)), 'minute');
}
