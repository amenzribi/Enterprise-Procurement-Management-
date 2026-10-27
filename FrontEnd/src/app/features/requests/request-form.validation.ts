import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { decimalPlaces, normaliseDecimal } from './money';

/** Largest quantity and unit price accepted, so values stay exact as JSON numbers (≤ 15 significant digits). */
export const MAX_QUANTITY = 999_999_999;
export const MAX_UNIT_PRICE = 99_999_999_999;
export const MAX_DECIMALS = 4;
export const DEFAULT_MAX_LINES = 100;

/**
 * An error to show, as the API reports it: a stable code translated under `requests.errors.*`, with parameters
 * for the message (`{ max: 200 }`). Local validators use the same codes as the server.
 */
export interface ErrorInfo {
  code: string;
  params?: Record<string, unknown>;
}

const isBlank = (value: unknown) => value === null || value === undefined || (typeof value === 'string' && value.trim() === '');

export function decimalValidator(kind: 'quantity' | 'price'): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (isBlank(control.value)) {
      return null;
    }
    const normalised = normaliseDecimal(control.value as string);
    if (normalised === null) {
      return { 'invalid-value': true };
    }
    if (decimalPlaces(normalised) > MAX_DECIMALS) {
      return { 'max-decimals': { max: MAX_DECIMALS } };
    }
    const value = Number(normalised);
    if (kind === 'quantity' && value <= 0) {
      return { 'quantity-positive': true };
    }
    if (kind === 'price' && value < 0) {
      return { 'price-not-negative': true };
    }
    if (value > (kind === 'quantity' ? MAX_QUANTITY : MAX_UNIT_PRICE)) {
      return { 'too-large': true };
    }
    return null;
  };
}

/** Today's date as `yyyy-MM-dd` in the browser's time zone (the server re-checks in the company time zone). */
export function today(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export const notPastDate: ValidatorFn = (control) =>
  !isBlank(control.value) && String(control.value) < today() ? { 'required-date-past': true } : null;

export const requiredValue: ValidatorFn = (control) => (isBlank(control.value) ? { required: true } : null);

/** Maps a control's first error to the code and parameters shown to the user. */
export function errorInfo(errors: ValidationErrors | null): ErrorInfo | null {
  if (!errors) {
    return null;
  }
  const server = errors['server'] as ErrorInfo | undefined;
  if (server) {
    return server;
  }
  if (errors['maxlength']) {
    return { code: 'too-long', params: { max: errors['maxlength'].requiredLength } };
  }
  const [code] = Object.keys(errors);
  if (!code) {
    return null;
  }
  const value = errors[code];
  return typeof value === 'object' && value !== null ? { code, params: value as Record<string, unknown> } : { code };
}

/**
 * Translates a server field path (`lines[2].quantity`, `department`) to the form's control path
 * (`lines.2.quantity`, `departmentId`).
 */
export function controlPath(field: string): string {
  const aliases: Record<string, string> = {
    category: 'categoryId',
    department: 'departmentId',
    site: 'siteId',
    costCenter: 'costCenterId',
    costCentre: 'costCenterId',
    requester: 'requesterId',
    requestingCompany: 'requestingCompanyId',
    beneficiarySite: 'beneficiarySiteId',
  };
  const path = field.replace(/\[(\d+)\]/g, '.$1');
  const [head, ...rest] = path.split('.');
  return [aliases[head] ?? head, ...rest].join('.');
}
