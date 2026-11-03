import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';
import { safeReturnUrl } from './safe-return-url';
import { workspaceHome } from './workspace-home';

describe('auth guards', () => {
  const authenticated = signal(false);

  const me = signal<{ mustChangePassword?: boolean; permissions?: string[] } | null>(null);

  beforeEach(() => {
    me.set(null);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: authenticated, me } },
      ],
    });
  });

  const run = (guard: typeof authGuard, url = '/requests/12') =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  it('lets a signed-in user into the workspace', () => {
    authenticated.set(true);
    expect(run(authGuard)).toBe(true);
  });

  it('sends an anonymous user to sign-in and remembers where they wanted to go', () => {
    authenticated.set(false);
    const result = run(authGuard) as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/sign-in?returnUrl=%2Frequests%2F12');
  });

  it('sends a signed-in user away from the sign-in page', () => {
    authenticated.set(true);
    me.set({ permissions: [] });
    expect(TestBed.inject(Router).serializeUrl(run(guestGuard) as UrlTree)).toBe('/overview');
  });

  it('sends a supplier contact to the portal home from the sign-in page', () => {
    authenticated.set(true);
    me.set({ permissions: ['MESSAGING_SUPPLIER'] });
    expect(TestBed.inject(Router).serializeUrl(run(guestGuard) as UrlTree)).toBe('/supplier/messages');
  });

  it('shows the sign-in page to anonymous users', () => {
    authenticated.set(false);
    expect(run(guestGuard)).toBe(true);
  });
});

describe('workspaceHome', () => {
  it('routes supplier contacts to the portal', () => {
    expect(workspaceHome({ permissions: ['MESSAGING_SUPPLIER'] } as never)).toBe('/supplier/messages');
  });

  it('routes internal users to requests', () => {
    expect(workspaceHome({ permissions: ['REQUEST_CREATE'] } as never)).toBe('/overview');
    expect(workspaceHome(null)).toBe('/overview');
  });
});

describe('safeReturnUrl', () => {
  it.each([
    ['/requests/12', '/requests/12'],
    ['/requests?status=DRAFT&page=2', '/requests?status=DRAFT&page=2'],
    ['/purchasing', '/purchasing'],
    ['/approvals', '/approvals'],
    ['/portal/home', '/portal/home'],
  ])('keeps the internal path %s', (input, expected) => {
    expect(safeReturnUrl(input)).toBe(expected);
  });

  it.each([
    'https://evil.example/phish',
    '//evil.example',
    '/\\evil.example',
    '\\\\evil.example',
    'javascript:alert(1)',
    '/javascript:alert(1)',
    'requests',
    '/sign-in?returnUrl=/requests',
    '/login',
    '/forgot-password',
    '/p',
    '/p#token',
    '/requests\n/next',
    '',
    null,
    undefined,
  ])('rejects %s and falls back to My work', (input) => {
    expect(safeReturnUrl(input as string | null | undefined)).toBe('/overview');
  });
});
