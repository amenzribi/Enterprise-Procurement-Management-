import { lineTotalMinor, minorToDecimalString, normaliseDecimal, toScaled } from './money';
import { controlPath, decimalValidator, errorInfo } from './request-form.validation';
import { FormControl } from '@angular/forms';

describe('request money rules', () => {
  it.each([
    ['12', '12'],
    ['12.5', '12.5'],
    ['12,5', '12.5'],
    ['1 234,5', '1234.5'],
    ['1 234,5', '1234.5'],
    ['abc', null],
    ['1.2.3', null],
    ['', null],
  ])('normalises %j to %j', (input, expected) => {
    expect(normaliseDecimal(input)).toBe(expected);
  });

  it('refuses more than 4 decimals when scaling', () => {
    expect(toScaled('1.2345')).toBe(12345n);
    expect(toScaled('1.23456')).toBeNull();
  });

  it('rounds a line total HALF_UP to the currency minor units', () => {
    // 3 × 0.0005 = 0.0015 -> 0.002 TND (3 minor units), 0.00 EUR (2), 0 JPY (0)
    expect(lineTotalMinor('3', '0.0005', 3)).toBe(2n);
    expect(lineTotalMinor('3', '0.0005', 2)).toBe(0n);
    // 2.5 × 1 = 2.5 -> 3 JPY (half up)
    expect(lineTotalMinor('2.5', '1', 0)).toBe(3n);
    // 10 × 4.115 = 41.15 EUR exactly (no floating point drift)
    expect(lineTotalMinor('10', '4.115', 2)).toBe(4115n);
    // 0.1 × 0.2 = 0.02 (floating point would give 0.020000000000000004)
    expect(lineTotalMinor('0.1', '0.2', 2)).toBe(2n);
  });

  it('returns null for incomplete or invalid lines so they do not count in the total', () => {
    expect(lineTotalMinor('', '4', 2)).toBeNull();
    expect(lineTotalMinor('2', 'x', 2)).toBeNull();
    expect(lineTotalMinor('2', '1.00001', 2)).toBeNull();
  });

  it('formats minor units as a plain decimal string', () => {
    expect(minorToDecimalString(1234500n, 3)).toBe('1234.500');
    expect(minorToDecimalString(5n, 2)).toBe('0.05');
    expect(minorToDecimalString(42n, 0)).toBe('42');
  });
});

describe('request form validation', () => {
  const quantity = decimalValidator('quantity');
  const price = decimalValidator('price');
  const check = (validator: typeof quantity, value: string) => errorInfo(validator(new FormControl(value)))?.code ?? null;

  it.each([
    ['0', 'quantity-positive'],
    ['-1', 'quantity-positive'],
    ['1.23456', 'max-decimals'],
    ['ten', 'invalid-value'],
    ['1000000000', 'too-large'],
    ['1.2345', null],
    ['', null],
  ])('checks quantity %j -> %j', (value, expected) => {
    expect(check(quantity, value)).toBe(expected);
  });

  it.each([
    ['0', null],
    ['-0.01', 'price-not-negative'],
    ['12,3456', null],
  ])('checks unit price %j -> %j', (value, expected) => {
    expect(check(price, value)).toBe(expected);
  });

  it('maps server field paths to form control paths', () => {
    expect(controlPath('lines[2].quantity')).toBe('lines.2.quantity');
    expect(controlPath('department')).toBe('departmentId');
    expect(controlPath('departmentId')).toBe('departmentId');
    expect(controlPath('title')).toBe('title');
  });

  it('prefers the server error over local errors and keeps message parameters', () => {
    expect(errorInfo({ required: true, server: { code: 'not-in-company' } })).toEqual({ code: 'not-in-company' });
    expect(errorInfo({ maxlength: { requiredLength: 5, actualLength: 6 } })).toEqual({ code: 'too-long', params: { max: 5 } });
    expect(errorInfo({ 'max-decimals': { max: 4 } })).toEqual({ code: 'max-decimals', params: { max: 4 } });
  });

  it('maps the requesting company and beneficiary site paths', () => {
    expect(controlPath('requestingCompany')).toBe('requestingCompanyId');
    expect(controlPath('beneficiarySite')).toBe('beneficiarySiteId');
  });
});
