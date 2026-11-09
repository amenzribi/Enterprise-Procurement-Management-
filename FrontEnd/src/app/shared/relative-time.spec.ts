import { untilText } from './relative-time';

describe('untilText', () => {
  const at = (day: number, hour: number) => new Date(2026, 10, day, hour, 0, 0);

  it('says "in 2 days" for a due date two calendar days away, even under 48 hours (QA-008)', () => {
    expect(untilText(at(11, 11).toISOString(), at(9, 13).getTime(), 'en')).toBe('in 2 days');
  });

  it('says "tomorrow" only for the next calendar date', () => {
    expect(untilText(at(10, 18).toISOString(), at(9, 13).getTime(), 'en')).toBe('tomorrow');
  });

  it('keeps hours for less than a day away', () => {
    expect(untilText(at(9, 18).toISOString(), at(9, 13).getTime(), 'en')).toBe('in 5 hours');
  });

  it('counts past dates by calendar day too', () => {
    expect(untilText(at(7, 15).toISOString(), at(9, 13).getTime(), 'en')).toBe('2 days ago');
  });
});
