import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { Me } from './auth.models';

export const DEFAULT_IDLE_TIMEOUT_SECONDS = 30 * 60;
/** The warning appears this long before the session expires (S1DR-11). */
export const WARNING_BEFORE_MS = 2 * 60 * 1000;
/** Clock and network slack before a reported expiry is read as the absolute limit. */
const ABSOLUTE_TOLERANCE_MS = 5000;
/** Shared between tabs of the same browser: the time of the latest API activity in any tab (S1CR-05). */
export const LAST_ACTIVITY_KEY = 'epi.session.lastActivity';

/**
 * Mirrors the server session's idle and absolute limits on the client, without polling the server: every API
 * response counts as activity (the server extends the session on each request). Two minutes before expiry it
 * raises a warning; at expiry it emits `expired` so the app can return the user to sign-in.
 *
 * All tabs share one server session, so activity is shared through localStorage: an idle tab follows the activity
 * of another tab instead of signing the user out (S1CR-05).
 *
 * It depends on nothing that uses HttpClient, so the HTTP interceptor can use it without a circular dependency
 * (QA1-D-03).
 */
@Injectable({ providedIn: 'root' })
export class SessionTimerService {
  private readonly document = inject(DOCUMENT);

  private idleMs = DEFAULT_IDLE_TIMEOUT_SECONDS * 1000;
  private absoluteDeadline: number | null = null;
  private lastActivity = 0;
  private running = false;
  private warningTimer: ReturnType<typeof setTimeout> | null = null;
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;
  private countdownTimer: ReturnType<typeof setInterval> | null = null;

  private readonly warningSignal = signal(false);
  /** True while the "your session is about to expire" warning should be shown. */
  readonly warning = this.warningSignal.asReadonly();

  private readonly secondsLeftSignal = signal(0);
  readonly secondsLeft = this.secondsLeftSignal.asReadonly();

  private readonly expiredSubject = new Subject<void>();
  /** The session ended: client-side expiry, or the server answered 401 to a request. */
  readonly expired: Observable<void> = this.expiredSubject.asObservable();

  constructor() {
    this.document.defaultView?.addEventListener('storage', (event) => {
      if (event.key === LAST_ACTIVITY_KEY && this.running) {
        this.followSharedActivity();
      }
    });
  }

  /** The server rejected a request because the session ended (401). */
  serverRejected(): void {
    this.expire();
  }

  start(me: Me): void {
    this.idleMs = (me.idleTimeoutSeconds && me.idleTimeoutSeconds > 0 ? me.idleTimeoutSeconds : DEFAULT_IDLE_TIMEOUT_SECONDS) * 1000;
    // The server reports min(last access + idle timeout, sign-in + absolute timeout). When that is earlier than the
    // idle deadline, the absolute limit is near and activity can no longer extend the session.
    const reported = me.sessionExpiresAt ? Date.parse(me.sessionExpiresAt) : NaN;
    const idleDeadline = Date.now() + this.idleMs;
    this.absoluteDeadline = Number.isFinite(reported) && reported < idleDeadline - ABSOLUTE_TOLERANCE_MS ? reported : null;
    this.running = true;
    this.touch();
  }

  /** Records activity (a successful API response) and reschedules the warning and expiry. */
  touch(): void {
    if (!this.running) {
      return;
    }
    this.lastActivity = Date.now();
    this.writeSharedActivity(this.lastActivity);
    this.schedule();
  }

  stop(): void {
    this.running = false;
    this.clearTimers();
    this.warningSignal.set(false);
  }

  /** Milliseconds until the session expires, or null when no session is tracked. */
  remainingMs(): number | null {
    return this.running ? Math.max(0, this.deadline() - Date.now()) : null;
  }

  private deadline(): number {
    const idleDeadline = this.lastActivity + this.idleMs;
    return this.absoluteDeadline === null ? idleDeadline : Math.min(idleDeadline, this.absoluteDeadline);
  }

  private schedule(): void {
    this.clearTimers();
    this.warningSignal.set(false);
    const now = Date.now();
    const deadline = this.deadline();
    if (deadline <= now) {
      this.expire();
      return;
    }
    this.warningTimer = setTimeout(() => this.showWarning(), Math.max(0, deadline - WARNING_BEFORE_MS - now));
    this.expiryTimer = setTimeout(() => this.expireUnlessActiveElsewhere(), deadline - now);
  }

  /** Another tab may have been active without this tab noticing (e.g. storage events throttled while hidden). */
  private expireUnlessActiveElsewhere(): void {
    if (this.followSharedActivity()) {
      return;
    }
    this.expire();
  }

  /** Adopts a more recent activity time from another tab; returns true when it extended this tab's session. */
  private followSharedActivity(): boolean {
    const shared = this.readSharedActivity();
    if (shared === null || shared <= this.lastActivity) {
      return false;
    }
    this.lastActivity = shared;
    this.schedule();
    return true;
  }

  private readSharedActivity(): number | null {
    try {
      const value = Number(this.document.defaultView?.localStorage.getItem(LAST_ACTIVITY_KEY));
      return Number.isFinite(value) && value > 0 ? value : null;
    } catch {
      return null;
    }
  }

  private writeSharedActivity(time: number): void {
    try {
      this.document.defaultView?.localStorage.setItem(LAST_ACTIVITY_KEY, String(time));
    } catch {
      // Without storage each tab keeps its own timer; the server session remains the authority.
    }
  }

  private showWarning(): void {
    this.updateCountdown();
    this.warningSignal.set(true);
    this.countdownTimer = setInterval(() => this.updateCountdown(), 1000);
  }

  private updateCountdown(): void {
    this.secondsLeftSignal.set(Math.max(0, Math.round((this.deadline() - Date.now()) / 1000)));
  }

  private expire(): void {
    this.stop();
    this.expiredSubject.next();
  }

  private clearTimers(): void {
    if (this.warningTimer) clearTimeout(this.warningTimer);
    if (this.expiryTimer) clearTimeout(this.expiryTimer);
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.warningTimer = this.expiryTimer = this.countdownTimer = null;
  }
}
