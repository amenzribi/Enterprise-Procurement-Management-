import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { Me } from '../../core/auth/auth.models';
import { LanguageService } from '../../core/i18n/language.service';
import { DashboardsPage } from './dashboards-page';

describe('DashboardsPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
        {
          provide: AuthService,
          useValue: {
            me: signal<Me | null>({
              id: 1,
              email: 'purchasing.manager@demo.local',
              displayName: 'Mehdi',
              company: { id: 1, name: 'CPA' },
              roles: ['PURCHASING_MANAGER'],
              permissions: ['DASHBOARD_VIEW', 'ERP_EXPORT'],
            }),
          },
        },
        { provide: LanguageService, useValue: { locale: () => 'en-GB', current: () => 'en' } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads pipeline and management list', async () => {
    const fixture = TestBed.createComponent(DashboardsPage);
    fixture.detectChanges();
    http.expectOne('/api/v1/dashboards/pipeline').flush({
      byUserStatus: [{ userStatus: 'IN_PROGRESS', count: 2 }],
      openAmountsByCurrency: [{ currency: 'TND', amount: '40.0000', requestCount: 2 }],
      byRequestingCompany: [],
      groupView: false,
    });
    http.expectOne((req) => req.url.startsWith('/api/v1/dashboards/management')).flush({
      items: [
        {
          id: 9,
          number: 'DEMO-1',
          title: 'Seals',
          userStatus: 'IN_PROGRESS',
          status: 'ASSIGNED_TO_BUYER',
          ownerName: 'Ines',
          ownerSince: '2026-10-01T10:00:00Z',
          requiredDate: '2026-11-01',
          totalAmount: '20.0000',
          currency: 'TND',
          late: false,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    });
    fixture.detectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent).toContain('Seals');
    expect(el.querySelector('a[href="/requests/9"]')).not.toBeNull();
    expect(el.textContent).toContain('dashboards.exportRequests');
  });
});
