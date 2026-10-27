import { HttpClient, HttpContext, HttpContextToken } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, switchMap, tap } from 'rxjs';
import { Language, LanguageService, isSupportedLanguage } from '../i18n/language.service';
import { NotificationService } from '../notifications/notification.service';
import { FormSnapshotStorage } from '../form-snapshots/form-snapshot-storage';
import { Me } from './auth.models';
import { SessionTimerService } from './session-timer.service';

/**
 * Marks requests whose 401 must not be treated as "session lost": sign-in itself, the initial `GET /me` and
 * sign-out.
 */
export const SKIP_SESSION_HANDLING = new HttpContextToken<boolean>(() => false);

export type AuthState = 'unknown' | 'authenticated' | 'anonymous';

const API = '/api/v1';
const skipSessionHandling = () => ({ context: new HttpContext().set(SKIP_SESSION_HANDLING, true) });

/**
 * Session state of the signed-in user. The server is the authority: identity, company and roles come from
 * `GET /me`, never from client-side state, and the session lives in an HttpOnly cookie the app cannot read.
 * State-changing requests carry the CSRF token through Angular's built-in XSRF support (cookie `XSRF-TOKEN`,
 * header `X-XSRF-TOKEN`); the token is re-read after sign-in and sign-out because the server rotates it.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly language = inject(LanguageService);
  private readonly notifications = inject(NotificationService);
  private readonly snapshots = inject(FormSnapshotStorage);
  private readonly timer = inject(SessionTimerService);

  private readonly meSignal = signal<Me | null>(null);
  readonly me = this.meSignal.asReadonly();

  private readonly stateSignal = signal<AuthState>('unknown');
  readonly state = this.stateSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.stateSignal() === 'authenticated');

  /** Language picked on the sign-in page during this visit; it wins over the account preference (S1DR-21). */
  private languageChosenWhileSignedOut: Language | null = null;
  /** True while the app redirects to sign-in after the session ended, so unsaved-changes guards let it pass. */
  private leavingForSignIn = false;

  constructor() {
    this.language.selections.subscribe((language) => {
      if (this.isAuthenticated()) {
        this.savePreferredLanguage(language);
      } else {
        this.languageChosenWhileSignedOut = language;
      }
    });
    this.timer.expired.subscribe(() => this.handleSessionLost());
  }

  get isLeavingForSignIn(): boolean {
    return this.leavingForSignIn;
  }

  /** Called once at start-up: prepares the CSRF cookie and restores an existing session. Never fails. */
  init(): Observable<void> {
    return this.refreshCsrfToken().pipe(
      switchMap(() => this.fetchMe()),
      tap((me) => {
        if (me) {
          this.applyAccountLanguage(me);
        }
      }),
      map(() => undefined),
    );
  }

  signIn(email: string, password: string): Observable<Me> {
    return this.http.post<Me>(`${API}/auth/login`, { email, password }, skipSessionHandling()).pipe(
      tap((me) => this.setSignedIn(me)),
      // The server issues a new CSRF token at sign-in; read it before the next state-changing request.
      switchMap((me) => this.refreshCsrfToken().pipe(map(() => me))),
      tap((me) => {
        if (this.languageChosenWhileSignedOut) {
          this.savePreferredLanguage(this.languageChosenWhileSignedOut);
          this.languageChosenWhileSignedOut = null;
        } else {
          this.applyAccountLanguage(me);
        }
      }),
    );
  }

  signOut(): Observable<void> {
    const userId = this.meSignal()?.id;
    return this.http.post<void>(`${API}/auth/logout`, {}, skipSessionHandling()).pipe(
      catchError(() => of(undefined)),
      tap(() => {
        if (userId !== undefined) {
          this.snapshots.clearUser(userId);
        }
        this.clearSession();
      }),
      switchMap(() => this.refreshCsrfToken()),
      switchMap(() => this.navigateToSignIn({ reason: 'signedOut' })),
      map(() => undefined),
    );
  }

  /** Re-reads the current user, which also extends the server session ("Stay signed in"). */
  refresh(): Observable<Me | null> {
    return this.http.get<Me>(`${API}/me`).pipe(
      tap((me) => this.setSignedIn(me)),
      catchError(() => of(null)),
    );
  }

  /**
   * The server answered 401 to a request of a signed-in user, or the client-side timer ran out: return to sign-in
   * with an explanation and come back to the current page afterwards. Unsaved form content stays in session
   * storage for restore.
   */
  handleSessionLost(): void {
    if (this.stateSignal() !== 'authenticated') {
      return;
    }
    const returnUrl = this.router.url;
    this.clearSession();
    this.navigateToSignIn({ reason: 'expired', returnUrl }).subscribe();
  }

  private fetchMe(): Observable<Me | null> {
    return this.http.get<Me>(`${API}/me`, skipSessionHandling()).pipe(
      tap((me) => this.setSignedIn(me)),
      catchError(() => {
        this.clearSession();
        return of(null);
      }),
    );
  }

  private refreshCsrfToken(): Observable<void> {
    return this.http.get<void>(`${API}/auth/csrf`, skipSessionHandling()).pipe(
      map(() => undefined),
      catchError(() => of(undefined)),
    );
  }

  private setSignedIn(me: Me): void {
    if (this.meSignal()?.id !== me.id) {
      this.snapshots.prepareFor(me.id);
    }
    this.meSignal.set(me);
    this.stateSignal.set('authenticated');
    this.timer.start(me);
  }

  private clearSession(): void {
    this.meSignal.set(null);
    this.stateSignal.set('anonymous');
    this.timer.stop();
  }

  private applyAccountLanguage(me: Me): void {
    const preferred = me.preferredLanguage;
    if (isSupportedLanguage(preferred) && preferred !== this.language.current()) {
      this.language.use(preferred, 'account').subscribe();
    }
  }

  private savePreferredLanguage(language: Language): void {
    // Fire and forget: the interface keeps the chosen language even if saving fails (S1DR-21).
    this.http.put<void>(`${API}/me/preferences`, { preferredLanguage: language }).subscribe({
      next: () => {
        const me = this.meSignal();
        if (me) {
          this.meSignal.set({ ...me, preferredLanguage: language });
        }
      },
      error: () => this.notifications.error('language.saveFailed'),
    });
  }

  private navigateToSignIn(queryParams: Record<string, string>): Observable<boolean> {
    this.leavingForSignIn = true;
    return new Observable<boolean>((subscriber) => {
      this.router
        .navigate(['/sign-in'], { queryParams })
        .then((done) => subscriber.next(done))
        .catch(() => subscriber.next(false))
        .finally(() => subscriber.complete());
    }).pipe(finalize(() => (this.leavingForSignIn = false)));
  }
}
