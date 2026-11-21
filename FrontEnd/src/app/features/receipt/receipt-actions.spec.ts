import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { PurchaseRequest } from '../requests/request.models';
import { ReceiptActions } from './receipt-actions';
import { PurchaseOrderLine, ReceiptDossier } from './receipt.models';

const ME = { id: 7, permissions: ['PURCHASING_MANAGE', 'PURCHASING_ACT', 'RECEIPT_ACT'] };

/** A goods line delivered in one shipment; the shipment line has the same id as the purchase-order line for brevity. */
function goodsLine(id: number, description: string, quantity: number, unit: string, received: number, receivable: number): PurchaseOrderLine {
  return {
    id, description, quantity, unit, received, receivable, kind: 'GOODS',
    shipmentLines: [{ id, shipmentId: 900, shipmentNumber: 'SH-1', quantity, received, receivable }],
  };
}

function dossier(changes: Partial<ReceiptDossier> = {}): ReceiptDossier {
  return {
    receipts: [],
    invoice: null,
    closure: { ready: false, missing: [], closed: false, rating: null },
    serviceAccepted: false,
    shortfallAccepted: false,
    purchaseOrder: { currency: 'EUR', total: 35, lines: [goodsLine(11, 'Seal', 4, 'PCE', 1, 3)] },
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

  it('leaves out a line entered as 0 and keeps fractional remainders exact', async () => {
    const lines = [goodsLine(11, 'Seal', 0.3, 'KG', 0.1, 0.2), goodsLine(12, 'Gasket', 2, 'PCE', 0, 2)];
    await open('DELIVERED', dossier({ purchaseOrder: { currency: 'EUR', total: 35, lines } }));
    expect(element().querySelector<HTMLInputElement>('#rcpt-received-11')!.value).toBe('0.2');
    const gasket = element().querySelector<HTMLInputElement>('#rcpt-received-12')!;
    gasket.value = '0';
    gasket.dispatchEvent(new Event('input'));
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const body = http.expectOne('/api/v1/receipts/requests/42/goods-receipts').request.body;
    expect(body.lines.map((l: { purchaseOrderLineId: number }) => l.purchaseOrderLineId)).toEqual([11]);
  });

  it('starts a second receipt from the new remaining quantity', async () => {
    await open('DELIVERED', dossier());
    const received = element().querySelector<HTMLInputElement>('#rcpt-received-11')!;
    received.value = '1';
    received.dispatchEvent(new Event('input'));
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/receipts/requests/42/goods-receipts').flush({});
    const next = { currency: 'EUR', total: 35, lines: [goodsLine(11, 'Seal', 4, 'PCE', 2, 2)] };
    http.expectOne('/api/v1/receipts/requests/42').flush(dossier({ purchaseOrder: next }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element().querySelector<HTMLInputElement>('#rcpt-received-11')!.value).toBe('2');
  });

  const type = (id: string, value: string) => {
    const field = element().querySelector<HTMLInputElement>(id)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    return field;
  };
  const recordButton = () => [...element().querySelectorAll('button')].find((b) => b.textContent?.includes('receipt.actions.record'))!;

  describe('Received and Accepted (QA-002)', () => {
    it('keeps Accepted in step with Received while it is still the default', async () => {
      await open('DELIVERED', dossier());
      type('#rcpt-received-11', '1');
      expect(element().querySelector<HTMLInputElement>('#rcpt-accepted-11')!.value).toBe('1');
      expect(element().querySelector('#rcpt-error-11')).toBeNull();
      expect(recordButton().disabled).toBe(false);
    });

    it('leaves room for what was rejected when Accepted is still the default', async () => {
      await open('DELIVERED', dossier());
      type('#rcpt-rejected-11', '1');
      expect(element().querySelector<HTMLInputElement>('#rcpt-accepted-11')!.value).toBe('2');
    });

    it('keeps a lower Accepted the user entered', async () => {
      await open('DELIVERED', dossier());
      type('#rcpt-accepted-11', '1');
      type('#rcpt-received-11', '2');
      expect(element().querySelector<HTMLInputElement>('#rcpt-accepted-11')!.value).toBe('1');
      expect(element().querySelector('#rcpt-error-11')).toBeNull();
    });

    it('shows a specific error before submission when a manual Accepted becomes too high', async () => {
      await open('DELIVERED', dossier());
      type('#rcpt-accepted-11', '3');
      const received = type('#rcpt-received-11', '1');
      expect(element().querySelector('#rcpt-error-11')?.textContent).toContain('receipt.actions.acceptedExceedsReceived');
      expect(received.getAttribute('aria-describedby')).toBe('rcpt-error-11');
      expect(recordButton().disabled).toBe(true);
      element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      http.expectNone('/api/v1/receipts/requests/42/goods-receipts');
    });

    it('refuses more than remains on the shipment line before submission', async () => {
      await open('DELIVERED', dossier());
      type('#rcpt-received-11', '4');
      expect(element().querySelector('#rcpt-error-11')?.textContent).toContain('receipt.actions.receivedExceedsRemaining');
    });

    it('still leaves out a zero line, even with a stale manual Accepted', async () => {
      await open('DELIVERED', dossier());
      type('#rcpt-accepted-11', '3');
      type('#rcpt-received-11', '0');
      expect(element().querySelector('#rcpt-error-11')).toBeNull();
      expect(recordButton().disabled).toBe(true);
    });
  });

  it('records against the specific shipment line and offers one row per delivered shipment', async () => {
    const line: PurchaseOrderLine = {
      id: 11, description: 'Seal', quantity: 5, unit: 'PCE', received: 0, receivable: 5, kind: 'GOODS',
      shipmentLines: [
        { id: 71, shipmentId: 1, shipmentNumber: 'SH-1', quantity: 3, received: 0, receivable: 3 },
        { id: 72, shipmentId: 2, shipmentNumber: 'SH-2', quantity: 2, received: 0, receivable: 2 },
      ],
    };
    await open('DELIVERED', dossier({ purchaseOrder: { currency: 'EUR', total: 35, lines: [line] } }));
    type('#rcpt-received-72', '0');
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const body = http.expectOne('/api/v1/receipts/requests/42/goods-receipts').request.body;
    expect(body.lines).toHaveLength(1);
    expect(body.lines[0]).toMatchObject({ purchaseOrderLineId: 11, shipmentLineId: 71, quantityReceived: 3 });
  });

  it('offers no receipt row for a line that has not been delivered', async () => {
    const line: PurchaseOrderLine = { id: 11, description: 'Seal', quantity: 5, unit: 'PCE', received: 0, receivable: 0, kind: 'GOODS', shipmentLines: [] };
    await open('DELIVERED', dossier({ purchaseOrder: { currency: 'EUR', total: 35, lines: [line] } }));
    expect(element().querySelector('[id^="rcpt-received-"]')).toBeNull();
  });

  it('accepts service lines before delivery, and shows nothing for goods at that stage', async () => {
    const service: PurchaseOrderLine = { id: 12, description: 'Fitting', quantity: 1, unit: 'PCE', received: 0, receivable: 0, kind: 'SERVICE', shipmentLines: [] };
    await open('ORDER_CONFIRMED', dossier({ purchaseOrder: { currency: 'EUR', total: 35, lines: [service] } }));
    type('#service-comment', 'Work completed');
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    expect(http.expectOne('/api/v1/receipts/requests/42/service-acceptance').request.body).toEqual({ requestVersion: 3, comment: 'Work completed' });
  });

  it('hides the panel before delivery for an order with no service line', async () => {
    await open('ORDER_CONFIRMED', dossier());
    expect(element().querySelector('section')).toBeNull();
  });

  it('marks earlier problems as open or resolved (QA-004)', async () => {
    const receipt = (state: string) => ({ ...confirmedReceipt(), lines: [{ id: 1, description: 'Seal', quantityReceived: 1, quantityAccepted: 0, quantityRejected: 1, quantityDamaged: 0, condition: 'NON_CONFORMING', nonConformity: state }] });
    await open('NON_CONFORMING', dossier({ receipts: [receipt('OPEN'), { ...receipt('RESOLVED'), id: 6 }] as never }));
    expect(element().textContent).toContain('receipt.nonConformity.OPEN');
    expect(element().textContent).toContain('receipt.nonConformity.RESOLVED');
  });

  it('clears an earlier invoice error once the save succeeds (QA-005)', async () => {
    await open('ACCEPTED', dossier());
    type('#inv-no', 'INV-1');
    const form = element().querySelector<HTMLInputElement>('#inv-no')!.closest('form')!;
    form.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/receipts/requests/42/invoice').flush({ code: 'validation-failed' }, { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element().querySelector('.error')).not.toBeNull();
    form.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    expect(element().querySelector('.error')).toBeNull();
    http.expectOne('/api/v1/receipts/requests/42/invoice').flush({});
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element().querySelector('.error')).toBeNull();
  });

  it('moves focus to the panel heading when nothing is left to receive (QA-006)', async () => {
    document.body.appendChild(fixture.nativeElement);
    await open('DELIVERED', dossier());
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/receipts/requests/42/goods-receipts').flush({});
    http.expectOne('/api/v1/receipts/requests/42').flush(dossier({ purchaseOrder: { currency: 'EUR', total: 35, lines: [goodsLine(11, 'Seal', 4, 'PCE', 4, 0)] } }));
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.activeElement?.id).toBe('receipt-title');
    fixture.nativeElement.remove();
  });

  it('explains why closure is unavailable and disables Close (QA-007)', async () => {
    await open('INVOICED', dossier({ closure: { ready: false, missing: ['match-attested'], closed: false, rating: null } }));
    const close = [...element().querySelectorAll('button')].find((b) => b.textContent?.includes('receipt.actions.close'))!;
    expect(close.disabled).toBe(true);
    expect(close.getAttribute('aria-describedby')).toBe('closure-why');
    expect(element().querySelector('#closure-why')?.textContent).toContain('receipt.closureMissingItem.match-attested');
  });

  it('lets a manager close with a waiver note when only a quantity is outstanding', async () => {
    await open('INVOICED', dossier({ closure: { ready: false, missing: ['outstanding-quantity'], closed: false, rating: null } }));
    const close = () => [...element().querySelectorAll('button')].find((b) => b.textContent?.includes('receipt.actions.close'))!;
    expect(close().disabled).toBe(true);
    type('#waiver', 'Agreed with supplier');
    expect(close().disabled).toBe(false);
  });

  it('asks for another person to confirm a receipt the user recorded', async () => {
    await open('PARTIALLY_RECEIVED', dossier({ receipts: [{ ...confirmedReceipt(), status: 'RECORDED', receivedByUserId: ME.id }] as never }));
    expect(element().textContent).toContain('receipt.actions.otherMustConfirm');
    expect([...element().querySelectorAll('button')].some((b) => b.textContent?.includes('receipt.actions.confirm'))).toBe(false);
  });

  it('lets another person confirm the receipt', async () => {
    await open('PARTIALLY_RECEIVED', dossier({ receipts: [{ ...confirmedReceipt(), status: 'RECORDED', receivedByUserId: 99 }] as never }));
    expect([...element().querySelectorAll('button')].some((b) => b.textContent?.includes('receipt.actions.confirm'))).toBe(true);
  });

  it('lets purchasing attest a recorded invoice', async () => {
    const invoice = { id: 1, invoiceNumber: 'I', amount: 35, currency: 'EUR', documentReference: null, matchAttested: false, paymentStatus: 'PENDING' };
    await open('INVOICED', dossier({ invoice }));
    const button = [...element().querySelectorAll('button')].find((b) => b.textContent?.includes('receipt.actions.attestNow'))!;
    button.click();
    expect(http.expectOne('/api/v1/receipts/requests/42/invoice/match-attested').request.body).toEqual({ requestVersion: 3 });
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

  it('offers neither attestation nor payment to the person who recorded the invoice', async () => {
    const invoice = { id: 1, invoiceNumber: 'I', amount: 35, currency: 'EUR', documentReference: null, matchAttested: false, paymentStatus: 'PENDING', recordedByUserId: ME.id };
    await open('INVOICED', dossier({ invoice }));
    const labels = [...element().querySelectorAll('button')].map((b) => b.textContent ?? '');
    expect(labels.some((text) => text.includes('receipt.actions.attestNow'))).toBe(false);
    expect(labels.some((text) => text.includes('receipt.actions.markPaid'))).toBe(false);
  });

  it('records an invoice without attesting it: attestation is another person’s step', async () => {
    await open('ACCEPTED', dossier());
    const form = element().querySelector<HTMLInputElement>('#inv-no')!.closest('form')!;
    expect(form.querySelector('input[type="checkbox"]')).toBeNull();
    expect(form.textContent).toContain('receipt.actions.invoiceSecondPerson');
  });
});
