import { importRowTone } from './import-page';

describe('importRowTone', () => {
  it('marks accepted rows success and rejected rows danger', () => {
    expect(importRowTone('ACCEPTED')).toBe('success');
    expect(importRowTone('REJECTED')).toBe('danger');
    expect(importRowTone('VALIDATING')).toBe('neutral');
  });
});
