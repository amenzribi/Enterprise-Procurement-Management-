import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SKIP_SESSION_HANDLING } from './auth.service';
import { sessionInterceptor } from './session.interceptor';
import { SessionTimerService } from './session-timer.service';

describe('sessionInterceptor', () => {
  const timer = { touch: vi.fn(), serverRejected: vi.fn() };
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    timer.serverRejected.mockReset();
    timer.touch.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionInterceptor])),
        provideHttpClientTesting(),
        { provide: SessionTimerService, useValue: timer },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('extends the client session timer on every successful API response', () => {
    http.get('/api/v1/requests').subscribe();
    controller.expectOne('/api/v1/requests').flush({ items: [] });

    expect(timer.touch).toHaveBeenCalledTimes(1);
  });

  it('does not count non-API responses (translation files) as session activity', () => {
    http.get('i18n/en.json').subscribe();
    controller.expectOne('i18n/en.json').flush({});

    expect(timer.touch).not.toHaveBeenCalled();
  });

  it('sends the user to sign-in when an API request answers 401', () => {
    let failed = false;
    http.get('/api/v1/requests/7').subscribe({ error: () => (failed = true) });
    controller.expectOne('/api/v1/requests/7').flush({ code: 'session-expired' }, { status: 401, statusText: 'Unauthorized' });

    expect(timer.serverRejected).toHaveBeenCalledTimes(1);
    expect(failed).toBe(true);
  });

  it('leaves 401s of sign-in and the initial session check to their callers', () => {
    const context = new HttpContext().set(SKIP_SESSION_HANDLING, true);
    http.post('/api/v1/auth/login', {}, { context }).subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/auth/login').flush({ code: 'invalid-credentials' }, { status: 401, statusText: 'Unauthorized' });

    expect(timer.serverRejected).not.toHaveBeenCalled();
  });

  it('re-fetches the CSRF token and retries once when the server rejects the token', () => {
    let result: unknown;
    http.post('/api/v1/requests', { title: 'x' }).subscribe((body) => (result = body));
    controller.expectOne('/api/v1/requests').flush({ code: 'csrf-invalid' }, { status: 403, statusText: 'Forbidden' });
    controller.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    controller.expectOne('/api/v1/requests').flush({ id: 1 });

    expect(result).toEqual({ id: 1 });
  });

  it('does not retry a second CSRF rejection', () => {
    let failed = false;
    http.post('/api/v1/requests', {}).subscribe({ error: () => (failed = true) });
    controller.expectOne('/api/v1/requests').flush({ code: 'csrf-invalid' }, { status: 403, statusText: 'Forbidden' });
    controller.expectOne('/api/v1/auth/csrf').flush(null, { status: 204, statusText: 'No Content' });
    controller.expectOne('/api/v1/requests').flush({ code: 'csrf-invalid' }, { status: 403, statusText: 'Forbidden' });

    expect(failed).toBe(true);
    controller.expectNone('/api/v1/auth/csrf');
  });

  it('does not treat 403 as a lost session', () => {
    http.get('/api/v1/requests/7').subscribe({ error: () => undefined });
    controller.expectOne('/api/v1/requests/7').flush({ code: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    expect(timer.serverRejected).not.toHaveBeenCalled();
  });
});
