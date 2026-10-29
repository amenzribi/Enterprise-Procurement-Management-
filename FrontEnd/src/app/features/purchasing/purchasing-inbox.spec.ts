import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { convertToParamMap, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { gapList, PurchasingInboxPage, queueQueryFromParams } from './purchasing-inbox';
import { QueueItem } from './purchasing.models';

const ITEM: QueueItem = {
  requestId: 42,
  version: 3,
  number: 'DEMO-TN-PLT-PR-2026-000501',
  title: 'Seals for P-204',
  status: 'IN_PURCHASING_REVIEW',
  userStatus: 'APPROVED',
  requester: { id: 1, name: 'Sami' },
  buyingCompany: { id: 9, code: 'DEMO-CPA', name: 'CPA' },
  requestingCompany: { id: 3, code: 'DEMO-TN-PLT', name: 'Sfax' },
  amount: '300',
  currency: 'TND',
  completenessGaps: ['category'],
};

describe('queueQueryFromParams', () => {
  it('accepts only known filters and bounds the search text', () => {
    const query = queueQueryFromParams(
      convertToParamMap({ status: 'DRAFT', sort: 'password', page: '-1', q: 'x'.repeat(300), mode: 'MIXED' }),
    );
    expect(query.status).toBeNull();
    expect(query.purchaseMode).toBeNull();
    expect(query.sort).toBe('requiredDate');
    expect(query.page).toBe(0);
    expect(query.q?.length).toBe(100);
  });
});

describe('gapList', () => {
  it('joins completeness gaps for the card', () => {
    expect(gapList(ITEM)).toBe('category');
  });
});

describe('PurchasingInboxPage', () => {
  let fixture: ComponentFixture<PurchasingInboxPage>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [PurchasingInboxPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PurchasingInboxPage);
    await fixture.whenStable();
  });

  afterEach(() => {
    http.match('/api/v1/purchasing/queue/count').forEach((request) => request.flush({ pending: 1 }));
    http.verify();
  });

  it('asks for the queue by required date and shows each request as a card', async () => {
    const request = http.expectOne((call) => call.url === '/api/v1/purchasing/queue');
    expect(request.request.params.get('sort')).toBe('requiredDate');
    request.flush({ items: [ITEM], page: 0, size: 20, totalItems: 1 });
    await fixture.whenStable();

    const card = element().querySelector('.card')!;
    expect(card.textContent).toContain('DEMO-TN-PLT-PR-2026-000501');
    expect(card.textContent).toContain('Sami');
    expect(card.textContent).toContain('purchasing.inbox.unassigned');
    expect(card.querySelector('a[href="/requests/42"]')).not.toBeNull();
  });
});
