import { TestBed } from '@angular/core/testing';
import { Me } from './auth.models';
import { LAST_ACTIVITY_KEY, SessionTimerService, WARNING_BEFORE_MS } from './session-timer.service';

const MINUTE = 60_000;

function me(overrides: Partial<Me> = {}): Me {
  return {
    id: 1,
    email: 'requester.tn@demo.local',
    displayName: 'Requester',
    company: { id: 3, name: 'Plant' },
    roles: ['REQUESTER'],
    idleTimeoutSeconds: 30 * 60,
    ...overrides,
  };
}

describe('SessionTimerService', () => {
  let timer: SessionTimerService;
  let expired: number;

  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-25T08:00:00Z'));
    timer = TestBed.inject(SessionTimerService);
    expired = 0;
    timer.expired.subscribe(() => expired++);
  });

  afterEach(() => {
    timer.stop();
    vi.useRealTimers();
  });

  it('warns two minutes before the idle timeout and expires at the timeout', () => {
    timer.start(me());

    vi.advanceTimersByTime(28 * MINUTE - 1);
    expect(timer.warning()).toBe(false);

    vi.advanceTimersByTime(1);
    expect(timer.warning()).toBe(true);
    expect(timer.secondsLeft()).toBe(WARNING_BEFORE_MS / 1000);

    vi.advanceTimersByTime(2 * MINUTE);
    expect(expired).toBe(1);
    expect(timer.warning()).toBe(false);
  });

  it('restarts the idle period on activity, which also hides the warning', () => {
    timer.start(me());
    vi.advanceTimersByTime(29 * MINUTE);
    expect(timer.warning()).toBe(true);

    timer.touch();

    expect(timer.warning()).toBe(false);
    vi.advanceTimersByTime(29 * MINUTE);
    expect(expired).toBe(0);
    vi.advanceTimersByTime(MINUTE);
    expect(expired).toBe(1);
  });

  it('never extends past the absolute session limit reported by the server, whatever the activity', () => {
    // 10 minutes left although the idle timeout is 30: the 8-hour limit is near.
    timer.start(me({ sessionExpiresAt: new Date(Date.now() + 10 * MINUTE).toISOString() }));

    vi.advanceTimersByTime(5 * MINUTE);
    timer.touch();
    vi.advanceTimersByTime(5 * MINUTE);

    expect(expired).toBe(1);
  });

  it('treats a reported expiry equal to the idle deadline as an ordinary idle session', () => {
    timer.start(me({ sessionExpiresAt: new Date(Date.now() + 30 * MINUTE).toISOString() }));
    vi.advanceTimersByTime(20 * MINUTE);
    timer.touch();
    vi.advanceTimersByTime(20 * MINUTE);
    expect(expired).toBe(0);
  });

  it('uses the server idle timeout when given, and 30 minutes otherwise', () => {
    timer.start(me({ idleTimeoutSeconds: 120 }));
    expect(timer.remainingMs()).toBe(2 * MINUTE);

    timer.start(me({ idleTimeoutSeconds: null }));
    expect(timer.remainingMs()).toBe(30 * MINUTE);
  });

  it('follows activity in another tab instead of expiring (S1CR-05)', () => {
    timer.start(me());
    vi.advanceTimersByTime(29 * MINUTE);
    expect(timer.warning()).toBe(true);

    // Another tab made an API call: it wrote the shared activity time and the browser fired a storage event.
    localStorage.setItem(LAST_ACTIVITY_KEY, String(Date.now()));
    window.dispatchEvent(new StorageEvent('storage', { key: LAST_ACTIVITY_KEY }));

    expect(timer.warning()).toBe(false);
    vi.advanceTimersByTime(29 * MINUTE);
    expect(expired).toBe(0);
    vi.advanceTimersByTime(MINUTE);
    expect(expired).toBe(1);
  });

  it('checks the shared activity before expiring, even if no storage event arrived', () => {
    timer.start(me());
    vi.advanceTimersByTime(20 * MINUTE);
    localStorage.setItem(LAST_ACTIVITY_KEY, String(Date.now()));

    vi.advanceTimersByTime(10 * MINUTE);
    expect(expired).toBe(0);
    vi.advanceTimersByTime(20 * MINUTE);
    expect(expired).toBe(1);
  });

  it('shares its own activity with other tabs', () => {
    timer.start(me());
    vi.advanceTimersByTime(MINUTE);
    timer.touch();
    expect(localStorage.getItem(LAST_ACTIVITY_KEY)).toBe(String(Date.now()));
  });

  it('reports a session the server rejected', () => {
    timer.start(me());
    timer.serverRejected();
    expect(expired).toBe(1);
    expect(timer.remainingMs()).toBeNull();
  });

  it('does nothing after stop, e.g. after sign-out', () => {
    timer.start(me());
    timer.stop();
    timer.touch();

    vi.advanceTimersByTime(60 * MINUTE);
    expect(expired).toBe(0);
    expect(timer.remainingMs()).toBeNull();
  });
});
