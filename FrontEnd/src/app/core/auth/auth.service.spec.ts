import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { LanguageService } from '../i18n/language.service';
import { FormSnapshotStorage } from '../form-snapshots/form-snapshot-storage';
import { Me } from './auth.models';
import { AuthService } from './auth.service';
import { SessionTimerService } from './session-timer.service';

const ME: Me = {
  id: 11,
  email: 'requester.tn@demo.local',
  displayName: 'Salma Ben Ali',
  company: { id: 3, code: 'DEMO-TN-PLT', name: 'Atlas Mechanical Sfax (Demo)' },
  roles: ['REQUESTER'],
  preferredLanguage: 'fr',
  idleTimeoutSeconds: 1800,
};

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let language: LanguageService;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    language = TestBed.inject(LanguageService);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    TestBed.inject(SessionTimerService).stop();
    http.verify();
  });

  async function signIn(me: Me = ME): Promise<void> {
    const result = firstValueFrom(auth.signIn(' requester.tn@demo.local ', 'secret'));
    const login = http.expectOne('/api/v1/auth/login');
    expect(login.request.method).toBe('POST');
    login.flush(me);
    http.expectOne('/api/v1/auth/csrf').flush(null);
    await result;
  }

  it('restores an existing session at start-up and applies the account language', async () => {
    const init = firstValueFrom(auth.init());
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/me').flush(ME);
    await init;

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.me()?.company.code).toBe('DEMO-TN-PLT');
    expect(language.current()).toBe('fr');
  });

  it('starts anonymous when there is no session, without failing start-up', async () => {
    const init = firstValueFrom(auth.init());
    http.expectOne('/api/v1/auth/csrf').flush(null);
    http.expectOne('/api/v1/me').flush({ code: 'unauthenticated' }, { status: 401, statusText: 'Unauthorized' });
    await init;

    expect(auth.state()).toBe('anonymous');
    expect(auth.me()).toBeNull();
  });

  it('signs in, re-reads the rotated CSRF token and applies the account language', async () => {
    await signIn();

    expect(auth.isAuthenticated()).toBe(true);
    expect(language.current()).toBe('fr');
  });

  it('saves a language chosen on the sign-in page to the account instead of applying the account one', async () => {
    await firstValueFrom(language.use('en'));
    await signIn();

    const save = http.expectOne('/api/v1/me/preferences');
    expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ preferredLanguage: 'en' });
    save.flush(null);
    expect(language.current()).toBe('en');
  });

  it('saves a language switch made while signed in to the account (S1-AC-12)', async () => {
    await signIn({ ...ME, preferredLanguage: 'en' });

    await firstValueFrom(language.use('fr'));

    const save = http.expectOne('/api/v1/me/preferences');
    expect(save.request.body).toEqual({ preferredLanguage: 'fr' });
    save.flush(null);
    expect(auth.me()?.preferredLanguage).toBe('fr');
  });

  it('signs out on the server, forgets the user and clears their unsaved form copies', async () => {
    await signIn();
    const snapshots = TestBed.inject(FormSnapshotStorage);
    snapshots.save(ME.id, 'request.new', { version: null, savedAt: '', value: { title: 'x' } });

    const done = firstValueFrom(auth.signOut());
    http.expectOne('/api/v1/auth/logout').flush(null);
    http.expectOne('/api/v1/auth/csrf').flush(null);
    await done;

    expect(auth.isAuthenticated()).toBe(false);
    expect(snapshots.load(ME.id, 'request.new')).toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/sign-in'], { queryParams: { reason: 'signedOut' } });
  });

  it('removes another user\'s form copies left in this tab at sign-in (S1CR-06)', async () => {
    const snapshots = TestBed.inject(FormSnapshotStorage);
    snapshots.save(99, 'request.new', { version: null, savedAt: new Date().toISOString(), value: { title: 'other user' } });

    await signIn();

    expect(snapshots.load(99, 'request.new')).toBeNull();
  });

  it('returns to sign-in when the server rejects a request (reported through the session timer)', async () => {
    await signIn();
    TestBed.inject(SessionTimerService).serverRejected();

    expect(auth.isAuthenticated()).toBe(false);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/sign-in'], {
      queryParams: { reason: 'expired', returnUrl: '/' },
    });
  });

  it('returns to sign-in with the current page when the session is lost, keeping form copies', async () => {
    await signIn();
    const snapshots = TestBed.inject(FormSnapshotStorage);
    snapshots.save(ME.id, 'request.new', { version: null, savedAt: new Date().toISOString(), value: { title: 'x' } });

    auth.handleSessionLost();

    expect(auth.isAuthenticated()).toBe(false);
    expect(snapshots.load(ME.id, 'request.new')).not.toBeNull();
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/sign-in'], {
      queryParams: { reason: 'expired', returnUrl: '/' },
    });
  });
});
