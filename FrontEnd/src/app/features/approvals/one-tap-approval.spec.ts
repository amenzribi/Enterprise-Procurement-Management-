import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { OneTapApprovalPage } from './one-tap-approval';

const TOKEN = 'k3J9-q_Zx8VtR2mNpL5cW7yH1sD4fG6jA0bE3uI9oQ';

describe('OneTapApprovalPage', () => {
  let fixture: ComponentFixture<OneTapApprovalPage>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;

  async function open(hash: string) {
    history.replaceState(null, '', `/a${hash}`);
    TestBed.configureTestingModule({
      imports: [OneTapApprovalPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OneTapApprovalPage);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    history.replaceState(null, '', '/');
  });

  it('reads the token from the fragment, removes it from the address bar, and sends it only in a POST body', async () => {
    await open(`#${TOKEN}`);

    expect(location.hash).toBe('');
    expect(location.pathname).toBe('/a');
    const summary = http.expectOne('/api/v1/approval-links/summary');
    expect(summary.request.method).toBe('POST');
    expect(summary.request.body).toEqual({ token: TOKEN });
    expect(summary.request.urlWithParams).not.toContain(TOKEN);
  });

  it('never approves on opening: it shows the summary and waits for the confirm tap', async () => {
    await open(`#${TOKEN}`);
    http.expectOne('/api/v1/approval-links/summary').flush({
      number: 'DEMO-TN-PLT-PR-2026-000418',
      title: 'Mechanical seals for pump P-204',
      requester: { id: 1, displayName: 'Sami' },
      company: { id: 3, code: 'DEMO-TN-PLT', name: 'Atlas Mechanical Sfax (Demo)' },
      currency: 'TND',
      amount: '8400',
      lines: [
        { description: 'Mechanical seal', quantity: 2, unit: 'PCE' },
        { description: 'Bearing kit', quantity: 1, unit: 'PCE' },
      ],
      lineCount: 5,
      expiresAt: '2026-11-05T08:00:00Z',
    });
    await fixture.whenStable();

    http.expectNone('/api/v1/approval-links/approve');
    expect(element().textContent).toContain('DEMO-TN-PLT-PR-2026-000418');
    expect(element().querySelector('.lines li')?.textContent?.replace(/\s+/g, ' ').trim()).toBe('2 PCE — Mechanical seal');
    expect(element().textContent).toContain('oneTap.moreLines');
    expect(element().textContent).toContain('Sami');
    expect(element().textContent).toContain('oneTap.expires');
    expect(element().textContent).not.toContain(TOKEN);
  });

  it('approves with an idempotency key and reports an unreachable service', async () => {
    await open(`#${TOKEN}`);
    http.expectOne('/api/v1/approval-links/summary').flush({ number: 'N-1', title: 'Seals', currency: 'TND' });
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('button.approve')!.click();
    const first = http.expectOne('/api/v1/approval-links/approve');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    expect(first.request.body).toEqual({ token: TOKEN });
    first.flush({ code: 'internal-error' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();
    expect(element().textContent).toContain('oneTap.unavailable.title');
  });

  it('confirms the approval', async () => {
    await open(`#${TOKEN}`);
    http.expectOne('/api/v1/approval-links/summary').flush({ number: 'N-1', title: 'Seals', currency: 'TND' });
    await fixture.whenStable();

    element().querySelector<HTMLButtonElement>('button.approve')!.click();
    http.expectOne('/api/v1/approval-links/approve').flush({ outcome: 'APPROVED', number: 'N-1' });
    await fixture.whenStable();
    expect(element().textContent).toContain('oneTap.approved.title');
  });

  it('shows the same neutral page for every invalid link, whatever the server says', async () => {
    const pages: string[] = [];
    for (const [status, code] of [
      [404, 'link-invalid'],
      [404, 'link-invalid'],
      [403, 'csrf-invalid'],
    ] as const) {
      TestBed.resetTestingModule();
      await open(`#${TOKEN}`);
      http.expectOne('/api/v1/approval-links/summary').flush({ code }, { status, statusText: 'x' });
      await fixture.whenStable();
      pages.push(element().querySelector('main')!.textContent!.trim());
    }
    expect(new Set(pages).size).toBe(1);
    expect(pages[0]).toContain('oneTap.invalid.title');
  });

  it('asks to try later when the link is used too often (429)', async () => {
    await open(`#${TOKEN}`);
    http.expectOne('/api/v1/approval-links/summary').flush({ code: 'too-many-requests' }, { status: 429, statusText: 'Too Many' });
    await fixture.whenStable();
    expect(element().textContent).toContain('oneTap.unavailable.title');
  });

  it('does not call the server when the link has no usable token', async () => {
    await open('#short');
    http.expectNone('/api/v1/approval-links/summary');
    expect(element().textContent).toContain('oneTap.invalid.title');
  });
});
