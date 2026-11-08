import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PurchaseRequest } from '../requests/request.models';
import { ReceiptActions } from './receipt-actions';
import { ReceiptDossier } from './receipt.models';

const ME = { id: 7, permissions: ['PURCHASING_MANAGE', 'PURCHASING_ACT', 'RECEIPT_ACT'] };

function dossier(changes: Partial<ReceiptDossier> = {}): ReceiptDossier {
  return {
    receipts: [],
    invoice: null,
    closure: { ready: false, missing: [], closed: false, rating: null },
    serviceAccepted: false,
    shortfallAccepted: false,
    purchaseOrder: { currency: 'EUR', total: 35, lines: [{ id: 11, description: 'Seal', quantity: 4, unit: 'PCE', received: 1 }] },
    ...changes,
  };
}

function confirmedReceipt() {
  return { id: 5, number: 'GR-1', status: 'CONFIRMED', note: null, version: 1, lines: [], evidence: [] };
}

describe('ReceiptActions', () => {
  let fixture: ComponentFixture<ReceiptActions>;
  let http: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const open = async (status: string, value: ReceiptDossier) => {
    fixture.componentRef.setInput('request', { id: 42, version: 3, status, currency: 'TND' } as unknown as PurchaseRequest);
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne('/api/v1/receipts/requests/42').flush(value);
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ReceiptActions],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideTranslateService(),
        { provide: AuthService, useValue: { me: signal(ME) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReceiptActions);
  });

  it('starts each line at the remaining quantity and sends what the receiver entered', async () => {
    await open('DELIVERED', dossier());
    const received = element().querySelector<HTMLInputElement>('#rcpt-received-11')!;
    expect(received.value).toBe('3');

    const rejected = element().querySelector<HTMLInputElement>('#rcpt-rejected-11')!;
    rejected.value = '1';
    rejected.dispatchEvent(new Event('input'));
    const accepted = element().querySelector<HTMLInputElement>('#rcpt-accepted-11')!;
    accepted.value = '2';
    accepted.dispatchEvent(new Event('input'));
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

    const body = http.expectOne('/api/v1/receipts/requests/42/goods-receipts').request.body;
    expect(body.lines[0]).toMatchObject({ purchaseOrderLineId: 11, quantityReceived: 3, quantityAccepted: 2, quantityRejected: 1 });
  });

  it('offers acceptance with shortfall only once every receipt is confirmed', async () => {
    await open('NON_CONFORMING', dossier({ receipts: [confirmedReceipt()] as never }));
    expect(element().querySelector('#shortfall-reason')).not.toBeNull();
  });

  it('hides acceptance with shortfall while a receipt is unconfirmed', async () => {
    await open('NON_CONFORMING', dossier({ receipts: [{ ...confirmedReceipt(), status: 'RECORDED' }] as never }));
    expect(element().querySelector('#shortfall-reason')).toBeNull();
  });

  it('invoices in the purchase-order currency, starting from its total', async () => {
    await open('ACCEPTED', dossier());
    const number = element().querySelector<HTMLInputElement>('#inv-no')!;
    expect(number.value).toBe('');
    expect(element().querySelector<HTMLInputElement>('#inv-amt')!.value).toBe('35');
    number.value = 'INV-9';
    number.dispatchEvent(new Event('input'));
    const form = number.closest('form')!;
    form.dispatchEvent(new Event('submit'));
    expect(http.expectOne('/api/v1/receipts/requests/42/invoice').request.body.currency).toBe('EUR');
  });

  it('marks a pending invoice as paid', async () => {
    const invoice = { id: 1, invoiceNumber: 'I', amount: 35, currency: 'EUR', documentReference: null, matchAttested: true, paymentStatus: 'PENDING' };
    await open('INVOICED', dossier({ invoice }));
    const button = [...element().querySelectorAll('button')].find((b) => b.textContent?.includes('receipt.actions.markPaid'))!;
    button.click();
    expect(http.expectOne('/api/v1/receipts/requests/42/invoice/paid').request.body).toEqual({ requestVersion: 3 });
  });
});
