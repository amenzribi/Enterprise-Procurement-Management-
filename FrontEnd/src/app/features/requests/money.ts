/**
 * Exact decimal arithmetic for request estimates. Quantities and unit prices have at most 4 decimals
 * (DECIMAL(19,4)); a line total is quantity × unit price rounded HALF_UP to the currency's minor units, and the
 * request total is the sum of rounded line totals (S1DR-17). The server recomputes and is authoritative; the
 * client mirrors the rule so the form shows the same figures before saving.
 */

export const INPUT_SCALE = 4;

/**
 * Normalises user input ("1 234,5" or "1234.5") to a plain decimal string, or null when it is not a number.
 * Spaces (including the narrow no-break space used by French formatting) are ignored; a comma is a decimal mark.
 */
export function normaliseDecimal(input: string | number | null | undefined): string | null {
  if (input === null || input === undefined) {
    return null;
  }
  // \s covers the no-break and narrow no-break spaces French number formatting inserts.
  const text = String(input).replace(/\s/g, '').replace(',', '.');
  return /^-?\d+(\.\d+)?$/.test(text) ? text : null;
}

export function decimalPlaces(normalised: string): number {
  const dot = normalised.indexOf('.');
  return dot === -1 ? 0 : normalised.length - dot - 1;
}

/** Scales a normalised decimal with at most `scale` decimals to an integer (e.g. "1.5", 4 -> 15000n). */
export function toScaled(normalised: string, scale = INPUT_SCALE): bigint | null {
  if (decimalPlaces(normalised) > scale) {
    return null;
  }
  const negative = normalised.startsWith('-');
  const [whole, fraction = ''] = (negative ? normalised.slice(1) : normalised).split('.');
  const value = BigInt(whole + fraction.padEnd(scale, '0'));
  return negative ? -value : value;
}

/** Divides a non-negative scaled integer, rounding HALF_UP. */
function roundHalfUp(value: bigint, divisor: bigint): bigint {
  if (divisor === 1n) {
    return value;
  }
  const negative = value < 0n;
  const magnitude = negative ? -value : value;
  const rounded = (magnitude + divisor / 2n) / divisor;
  return negative ? -rounded : rounded;
}

/** Line total in minor units of the currency, or null when an input is missing or invalid. */
export function lineTotalMinor(quantity: unknown, unitPrice: unknown, minorUnits: number): bigint | null {
  const q = normaliseDecimal(quantity as string);
  const p = normaliseDecimal(unitPrice as string);
  if (q === null || p === null) {
    return null;
  }
  const qs = toScaled(q);
  const ps = toScaled(p);
  if (qs === null || ps === null) {
    return null;
  }
  // q × p has scale 8; bring it to the currency's minor units.
  return roundHalfUp(qs * ps, 10n ** BigInt(INPUT_SCALE * 2 - minorUnits));
}

/** Formats an amount held in minor units as a plain decimal string ("1234.500" for 3 minor units). */
export function minorToDecimalString(amount: bigint, minorUnits: number): string {
  const negative = amount < 0n;
  const digits = (negative ? -amount : amount).toString().padStart(minorUnits + 1, '0');
  const whole = minorUnits === 0 ? digits : digits.slice(0, -minorUnits);
  const fraction = minorUnits === 0 ? '' : `.${digits.slice(-minorUnits)}`;
  return `${negative ? '-' : ''}${whole}${fraction}`;
}

/**
 * Locale-aware currency formatting of a decimal string or number without going through floating point where
 * the platform supports formatting decimal strings.
 */
export function formatMoney(amount: string | number | null | undefined, currency: string, minorUnits: number, locale: string): string {
  if (amount === null || amount === undefined || amount === '') {
    return '';
  }
  try {
    const formatter = new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      minimumFractionDigits: minorUnits,
      maximumFractionDigits: minorUnits,
    });
    return formatter.format(amount as unknown as number);
  } catch {
    return `${amount} ${currency}`;
  }
}
