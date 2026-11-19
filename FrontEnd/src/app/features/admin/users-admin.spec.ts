import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { Me } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { UsersAdminPage } from './users-admin';

const ACCOUNT = {
  id: 42,
  email: 'old@demo.local',
  displayName: 'Mail Change',
  companyId: 1,
  companyCode: 'DEMO',
  active: true,
  preferredLanguage: 'en',
  demo: false,
  version: 0,
  roles: [],
  pendingApprovals: 0,
  assignedBuying: 0,
};

describe('UsersAdminPage e-mail correction', () => {
  let http: HttpTestingController;

  function create(permissions: string[]) {
    TestBed.configureTestingModule({
      imports: [UsersAdminPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
        {
          provide: AuthService,
          useValue: {
            me: signal<Me | null>({
              id: 1,
              email: 'admin@demo.local',
              displayName: 'Admin',
              company: { id: 1, name: 'Demo' },
              roles: [],
              permissions,
            } as unknown as Me),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(UsersAdminPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/admin/organization/companies').flush([]);
    http.expectOne((r) => r.url === '/api/v1/admin/accounts').flush([]);
    fixture.componentInstance.select(ACCOUNT.id);
    http.expectOne('/api/v1/admin/accounts/42').flush(ACCOUNT);
    return fixture;
  }

  afterEach(() => http.verify());

  it('is not offered to a company administrator, and does not query the server', () => {
    const fixture = create(['USER_ADMIN']);
    http.expectNone('/api/v1/admin/accounts/42/email-change');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.email-change')).toBeNull();
  });

  it('lets a global administrator request a correction and shows the pending state', () => {
    const fixture = create(['USER_ADMIN', 'USER_ADMIN_GLOBAL']);
    http.expectOne('/api/v1/admin/accounts/42/email-change').flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    const page = fixture.componentInstance;
    expect((fixture.nativeElement as HTMLElement).querySelector('.email-change form')).not.toBeNull();

    page.emailForm.setValue({ newEmail: 'new@demo.local', reason: '  Typo at creation ' });
    page.requestEmailChange(ACCOUNT.id);
    const post = http.expectOne('/api/v1/admin/accounts/42/email-change');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ newEmail: 'new@demo.local', reason: 'Typo at creation' });
    post.flush({ id: 7, status: 'PENDING_APPROVAL', oldEmail: ACCOUNT.email, newEmail: 'new@demo.local', reason: 'Typo at creation', requestedBy: 1, requestedAt: '2026-11-18T10:00:00Z' }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();
    expect(page.emailChange()?.status).toBe('PENDING_APPROVAL');
    const buttons = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.email-change button'));
    expect(buttons.map((b) => b.textContent!.trim())).toEqual(['usersAdmin.emailChange.approve', 'usersAdmin.emailChange.cancel']);

    page.cancelEmailChange(ACCOUNT.id);
    http.expectOne('/api/v1/admin/accounts/42/email-change/cancel').flush(null, { status: 204, statusText: 'No Content' });
    expect(page.emailChange()).toBeNull();
  });

  it('rejects a short reason before calling the server', () => {
    const fixture = create(['USER_ADMIN', 'USER_ADMIN_GLOBAL']);
    http.expectOne('/api/v1/admin/accounts/42/email-change').flush(null, { status: 204, statusText: 'No Content' });
    const page = fixture.componentInstance;
    page.emailForm.setValue({ newEmail: 'new@demo.local', reason: 'no' });
    page.requestEmailChange(ACCOUNT.id);
    http.expectNone('/api/v1/admin/accounts/42/email-change');
  });
});
