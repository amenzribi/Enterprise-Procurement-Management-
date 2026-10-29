import { supplierVisibilityKey } from './suppliers.api';

describe('supplierVisibilityKey', () => {
  it('maps SHARED and COMPANY to distinct translation keys', () => {
    expect(supplierVisibilityKey('SHARED')).toBe('suppliers.visibility.SHARED');
    expect(supplierVisibilityKey('COMPANY')).toBe('suppliers.visibility.COMPANY');
    expect(supplierVisibilityKey('OTHER')).toBe('suppliers.visibility.COMPANY');
  });
});
