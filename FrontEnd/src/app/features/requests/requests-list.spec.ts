import { provideHttpClient } from '@angular/common/http';
import { signal } from '@angular/core';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { Me } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { RequestListItem } from './request.models';
import { RequestsListPage, queryFromParams } from './requests-list';

const ITEMS: RequestListItem[] = [
  { id: 2, number: 'DEMO-TN-PLT-PR-2026-000002', title: 'Pump seals', status: 'CREATED', totalAmount: '480.500', currency: 'TND', requiredDate: '2026-11-20' },
  { id: 3, number: null, title: 'Safety gloves', status: 'DRAFT', totalAmount: 0, currency: 'TND' },
];

const REQUESTER: Me = {
  id: 1,
  email: 'requester.tn@demo.local',
  displayName: 'Sami Ben Ali (Demo)',
  company: { id: 101, name: 'Atlas Mechanical Sfax (Demo)' },
  roles: ['REQUESTER'],
  permissions: ['REQUEST_CREATE'],
};

describe('RequestsListPage', () => {
  let fixture: ComponentFixture<RequestsListPage>;
  let http: HttpTestingController;
  const me = signal<Me | null>(REQUESTER);
  const element = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    me.set(REQUESTER);
    TestBed.configureTestingModule({
      imports: [RequestsListPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: AuthService, useValue: { me } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RequestsListPage);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const pending = () => http.expectOne((request) => request.url === '/api/v1/requests');

  it('shows skeletons and announces loading until the list arrives', () => {
    fixture.detectChanges();
    expect(element().querySelector('app-skeleton')).not.toBeNull();
    expect(element().querySelector('[role="status"]')?.textContent).toContain('requests.list.loading');
    pending().flush({ items: [], page: 0, size: 20, totalItems: 0 });
  });

  it('asks the server for my requests, newest first, with paging', () => {
    const request = pending();
    expect(request.request.params.get('scope')).toBe('mine');
    expect(request.request.params.has('mine')).toBe(false);
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush({ items: [], page: 0, size: 20, totalItems: 0 });
  });

  it('renders one cell for every column header', async () => {
    pending().flush({ items: ITEMS, page: 0, size: 20, totalItems: 2 });
    await fixture.whenStable();

    const headers = element().querySelectorAll('thead th').length;
    expect(headers).toBeGreaterThan(0);
    expect(element().querySelectorAll('tbody tr').length).toBeGreaterThan(0);
    element()
      .querySelectorAll('tbody tr')
      .forEach((row) => expect(row.querySelectorAll('td').length).toBe(headers));
  });

  it('lists requests with their number, or "Draft" when not yet submitted', async () => {
    pending().flush({ items: ITEMS, page: 0, size: 20, totalItems: 2 });
    await fixture.whenStable();

    const rows = element().querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('DEMO-TN-PLT-PR-2026-000002');
    expect(rows[1].textContent).toContain('requests.list.draftNumber');
    expect(element().querySelector('a[href="/requests/2"]')).not.toBeNull();
  });

  it('invites the user to create a first request when there is none', async () => {
    pending().flush({ items: [], page: 0, size: 20, totalItems: 0 });
    await fixture.whenStable();

    expect(element().querySelector('app-empty-state')?.textContent).toContain('requests.list.empty.title');
    expect(element().querySelector('app-empty-state a[href="/requests/new"]')).not.toBeNull();
  });

  it('shows "New request" only with the REQUEST_CREATE permission and no scope tabs for plain requesters', async () => {
    pending().flush({ items: [], page: 0, size: 20, totalItems: 0 });
    await fixture.whenStable();
    expect(element().querySelector('app-page-header a[href="/requests/new"]')).not.toBeNull();
    expect(element().querySelector('.scopes')).toBeNull();
  });

  it('offers "Visible to me" to department managers and hides creation from them', async () => {
    me.set({ ...REQUESTER, roles: ['DEPARTMENT_MANAGER'], permissions: ['REQUEST_VIEW_DEPARTMENT'] });
    pending().flush({ items: [], page: 0, size: 20, totalItems: 0 });
    await fixture.whenStable();

    expect(element().querySelector('.scopes')).not.toBeNull();
    expect(element().querySelector('app-page-header a[href="/requests/new"]')).toBeNull();
  });

  it('explains a failure and loads again on retry', async () => {
    pending().flush({ code: 'internal-error' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    const error = element().querySelector('app-error-state');
    expect(error?.textContent).toContain('requests.list.error.title');

    error?.querySelector<HTMLButtonElement>('button')?.click();
    await fixture.whenStable();
    pending().flush({ items: ITEMS, page: 0, size: 20, totalItems: 2 });
    await fixture.whenStable();

    expect(element().querySelectorAll('tbody tr').length).toBe(2);
  });
});

describe('queryFromParams', () => {
  it('accepts only known sort columns and statuses (no arbitrary sort properties)', () => {
    const query = queryFromParams(convertToParamMap({ sort: 'requester.passwordHash', status: 'HACKED', page: '-3', dir: 'sideways' }));

    expect(query).toEqual({ scope: 'mine', status: null, q: null, sort: 'createdAt', direction: 'desc', page: 0, size: 20 });
  });

  it('reads valid filters, sort and page from the URL', () => {
    const query = queryFromParams(convertToParamMap({ scope: 'visible', sort: 'totalAmount', dir: 'asc', status: 'SUBMITTED', page: '2', q: ' pump ' }));

    expect(query).toEqual({ scope: 'visible', status: 'SUBMITTED', q: 'pump', sort: 'totalAmount', direction: 'asc', page: 2, size: 20 });
  });
});
