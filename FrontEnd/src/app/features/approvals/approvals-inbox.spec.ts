import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { ApprovalsInboxPage, inboxQueryFromParams } from './approvals-inbox';
import { InboxItem } from './approval.models';

const ITEM: InboxItem = {
  taskId: 11,
  taskVersion: 2,
  status: 'PENDING',
  requestId: 7,
  number: 'DEMO-TN-PLT-PR-2026-000418',
  title: 'Mechanical seals for pump P-204',
  userStatus: 'SUBMITTED',
  requester: { id: 1, displayName: 'Sami' },
  company: { id: 3, code: 'DEMO-TN-PLT', name: 'Atlas Mechanical Sfax (Demo)' },
  amount: '2500',
  currency: 'EUR',
  companyAmount: '8400',
  companyCurrency: 'TND',
  stageNo: 1,
  stageName: 'Department manager',
  policy: 'ANY',
  waitingSince: '2026-11-02T08:00:00Z',
  dueAt: '2020-01-01T00:00:00Z',
  overdue: true,
  questionOpen: true,
};

describe('inboxQueryFromParams', () => {
  it('accepts only known views and sorts, and bounds the search text', () => {
    const query = inboxQueryFromParams(convertToParamMap({ view: 'everything', sort: 'password', page: '-2', q: 'x'.repeat(300) }));
    expect(query.view).toBe('pending');
    expect(query.sort).toBe('dueAt');
    expect(query.page).toBe(0);
    expect(query.q?.length).toBe(100);
  });
});

describe('ApprovalsInboxPage', () => {
  let fixture: ComponentFixture<ApprovalsInboxPage>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ApprovalsInboxPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ApprovalsInboxPage);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.match('/api/v1/approvals/inbox/count').forEach((request) => request.flush({ pending: 1, overdue: 1 }));
    http.verify();
  });

  const inbox = () => http.expectOne((request) => request.url === '/api/v1/approvals/inbox');

  it('asks for the pending decisions, earliest due first, and shows each one as a card', async () => {
    const request = inbox();
    expect(request.request.params.get('status')).toBe('PENDING');
    expect(request.request.params.get('sort')).toBe('dueAt,asc');
    request.flush({ items: [ITEM], page: 0, size: 20, totalItems: 1 });
    await fixture.whenStable();

    const card = element().querySelector('.card')!;
    expect(card.textContent).toContain('DEMO-TN-PLT-PR-2026-000418');
    expect(card.textContent).toContain('Sami');
    expect(card.textContent).toContain('approvals.inbox.informationRequested');
    expect(card.classList).toContain('is-overdue-card');
    // The amount in company currency is shown when it differs from the request currency.
    expect(card.querySelector('.amount-secondary')?.textContent).toContain('TND');
    expect(card.querySelector('a[href="/requests/7"]')).not.toBeNull();
  });

  it('shows an empty state when nothing waits for the user', async () => {
    inbox().flush({ items: [], page: 0, size: 20, totalItems: 0 });
    await fixture.whenStable();
    expect(element().textContent).toContain('approvals.inbox.empty.pending.title');
  });

  it('shows an error with retry when the inbox cannot be loaded', async () => {
    inbox().flush({ code: 'internal-error' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();
    expect(element().textContent).toContain('approvals.inbox.loadError');
    element().querySelector<HTMLButtonElement>('app-error-state button')!.click();
    inbox().flush({ items: [], page: 0, size: 20, totalItems: 0 });
  });
});
