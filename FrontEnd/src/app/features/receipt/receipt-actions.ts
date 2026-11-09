import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { AuthService } from '../../core/auth/auth.service';
import { hasPermission } from '../../core/auth/auth.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { PurchaseRequest } from '../requests/request.models';
import { ReceiptApi } from './receipt.api';
import { ReceiptDossier, ReceiptLineDraft } from './receipt.models';

const RECEIPT_CONDITIONS = ['OK', 'DAMAGED', 'MISSING', 'NON_CONFORMING'] as const;

@Component({
  selector: 'app-receipt-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel decide" aria-labelledby="receipt-title">
        <h2 class="panel-title" id="receipt-title">{{ 'receipt.actions.title' | translate }}</h2>
        @if (error()) {
          <p class="error" role="alert">{{ 'errors.' + error() | translate }}</p>
        }

        @if (dossier(); as d) {
          @for (r of d.receipts; track r.id) {
            <article class="block">
              <p class="muted small">
                {{ r.number }} · {{ 'receipt.status.' + r.status | translate }}
              </p>
              <ul>
                @for (line of r.lines; track line.id) {
                  <li>
                    {{ line.description }} —
                    {{ 'receipt.actions.received' | translate }} {{ line.quantityReceived }},
                    {{ 'receipt.actions.accepted' | translate }} {{ line.quantityAccepted }},
                    {{ 'receipt.actions.rejected' | translate }} {{ line.quantityRejected }},
                    {{ 'receipt.condition.' + line.condition | translate }}
                  </li>
                }
              </ul>
              @if (r.status === 'RECORDED' && canConfirm()) {
                <button appButton type="button" [disabled]="busy()" (click)="confirm(r.id, r.version)">
                  {{ 'receipt.actions.confirm' | translate }}
                </button>
              }
            </article>
          }

          @if (canReceive() && openLines().length > 0 && isDeliveredStage()) {
            <form class="form" (submit)="$event.preventDefault(); create()" novalidate>
              @for (line of openLines(); track line.id) {
                <fieldset class="line">
                  <legend>{{ line.description }} <span class="muted small tabular">{{ line.received }} / {{ line.quantity }} {{ line.unit }}</span></legend>
                  <div class="line-fields">
                    <div>
                      <label class="label" [for]="'rcpt-received-' + line.id">{{ 'receipt.actions.received' | translate }}</label>
                      <input [id]="'rcpt-received-' + line.id" type="number" min="0" step="any" [value]="draft(line.id).received" (input)="setDraft(line.id, 'received', $any($event.target).value)" />
                    </div>
                    <div>
                      <label class="label" [for]="'rcpt-accepted-' + line.id">{{ 'receipt.actions.accepted' | translate }}</label>
                      <input [id]="'rcpt-accepted-' + line.id" type="number" min="0" step="any" [value]="draft(line.id).accepted" (input)="setDraft(line.id, 'accepted', $any($event.target).value)" />
                    </div>
                    <div>
                      <label class="label" [for]="'rcpt-rejected-' + line.id">{{ 'receipt.actions.rejected' | translate }}</label>
                      <input [id]="'rcpt-rejected-' + line.id" type="number" min="0" step="any" [value]="draft(line.id).rejected" (input)="setDraft(line.id, 'rejected', $any($event.target).value)" />
                    </div>
                    <div>
                      <label class="label" [for]="'rcpt-condition-' + line.id">{{ 'receipt.actions.condition' | translate }}</label>
                      <select [id]="'rcpt-condition-' + line.id" (change)="setDraft(line.id, 'condition', $any($event.target).value)">
                        @for (code of conditions; track code) {
                          <option [value]="code" [selected]="draft(line.id).condition === code">{{ 'receipt.condition.' + code | translate }}</option>
                        }
                      </select>
                    </div>
                  </div>
                </fieldset>
              }
              <label class="label" for="rcpt-note">{{ 'receipt.actions.note' | translate }}</label>
              <input id="rcpt-note" [value]="note()" (input)="note.set($any($event.target).value)" />
              <label class="label" for="rcpt-photo">{{ 'receipt.actions.photoRef' | translate }}</label>
              <input id="rcpt-photo" [value]="photoRef()" (input)="photoRef.set($any($event.target).value)" />
              <p class="muted small">{{ 'receipt.actions.photoHint' | translate }}</p>
              <button appButton type="submit" [disabled]="busy() || receiptLines().length === 0">{{ 'receipt.actions.record' | translate }}</button>
            </form>
          }

          @if (canAcceptShortfall(d)) {
            <form class="form" (submit)="$event.preventDefault(); acceptShortfall()" novalidate>
              <p class="strong">{{ 'receipt.actions.shortfallTitle' | translate }}</p>
              <p class="muted small">{{ 'receipt.actions.shortfallHint' | translate }}</p>
              <label class="label" for="shortfall-reason">{{ 'receipt.actions.shortfallReason' | translate }} *</label>
              <input id="shortfall-reason" [value]="shortfallReason()" (input)="shortfallReason.set($any($event.target).value)" />
              <button appButton type="submit" [disabled]="busy() || shortfallReason().trim().length < 3">{{ 'receipt.actions.shortfallAccept' | translate }}</button>
            </form>
          }

          @if (d.invoice; as inv) {
            <p class="muted small">
              {{ 'receipt.actions.invoice' | translate }}: {{ inv.invoiceNumber }} · {{ inv.currency }} {{ inv.amount }} ·
              {{ 'receipt.payment.' + inv.paymentStatus | translate }}
              @if (inv.matchAttested) {
                · {{ 'receipt.actions.matchAttested' | translate }}
              }
            </p>
            @if (!inv.matchAttested && canInvoice()) {
              <button appButton variant="secondary" type="button" [disabled]="busy()" (click)="attest()">{{ 'receipt.actions.attestNow' | translate }}</button>
            }
            @if (inv.paymentStatus === 'PENDING' && canInvoice()) {
              <button appButton variant="secondary" type="button" [disabled]="busy()" (click)="markPaid()">{{ 'receipt.actions.markPaid' | translate }}</button>
            }
          } @else if (canInvoice() && isAcceptedOrLater()) {
            <form class="form" (submit)="$event.preventDefault(); saveInvoice()" novalidate>
              <label class="label" for="inv-no">{{ 'receipt.actions.invoiceNumber' | translate }}</label>
              <input id="inv-no" [value]="invoiceNo()" (input)="invoiceNo.set($any($event.target).value)" />
              <label class="label" for="inv-amt">{{ 'receipt.actions.invoiceAmount' | translate }} ({{ d.purchaseOrder?.currency ?? request().currency }})</label>
              <input id="inv-amt" type="number" step="0.01" [value]="invoiceAmount()" (input)="invoiceAmount.set($any($event.target).value)" />
              <label class="label" for="inv-doc">{{ 'receipt.actions.docRef' | translate }}</label>
              <input id="inv-doc" [value]="docRef()" (input)="docRef.set($any($event.target).value)" />
              <label class="check">
                <input type="checkbox" [checked]="matchAttested()" (change)="matchAttested.set($any($event.target).checked)" />
                {{ 'receipt.actions.attestMatch' | translate }}
              </label>
              <button appButton type="submit" [disabled]="busy() || !invoiceNo().trim()">
                {{ 'receipt.actions.recordInvoice' | translate }}
              </button>
            </form>
          }

          @if (d.closure.closed) {
            <p class="muted">{{ 'receipt.actions.closed' | translate }}</p>
          } @else if (canClose()) {
            <form class="form" (submit)="$event.preventDefault(); close()" novalidate>
              @if (!d.closure.ready) {
                <p class="muted small">{{ 'receipt.actions.closureMissing' | translate }}:
                  @for (code of d.closure.missing; track code; let last = $last) {
                    {{ 'receipt.closureMissingItem.' + code | translate }}@if (!last) {, }
                  }
                </p>
              }
              <label class="label" for="score">{{ 'receipt.actions.rating' | translate }}</label>
              <input id="score" type="number" min="1" max="5" [value]="rating()" (input)="rating.set($any($event.target).value)" />
              @if (canManage() && d.closure.missing.includes('outstanding-quantity')) {
                <label class="label" for="waiver">{{ 'receipt.actions.waiver' | translate }}</label>
                <input id="waiver" [value]="waiver()" (input)="waiver.set($any($event.target).value)" />
              }
              <button appButton type="submit" [disabled]="busy()">{{ 'receipt.actions.close' | translate }}</button>
            </form>
          }
        }
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .form { display: grid; gap: var(--space-2); margin-block-end: var(--space-3); }
    .label { font-weight: var(--weight-medium); font-size: var(--text-sm); }
    .check { display: flex; gap: var(--space-2); align-items: center; font-size: var(--text-sm); }
    select, input:not([type='checkbox']) {
      min-block-size: 2.5rem;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
    }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
    .error { color: var(--color-danger); }
    .block { margin-block-end: var(--space-3); }
    .strong { font-weight: var(--weight-medium); }
    .tabular { font-variant-numeric: tabular-nums; }
    .line { margin: 0; padding: 0 0 var(--space-3); border: 0; border-block-end: 1px solid var(--color-line); }
    .line legend { padding: 0; font-weight: var(--weight-medium); }
    .line-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr)); gap: var(--space-2); margin-block-start: var(--space-2); }
    .line-fields input, .line-fields select { inline-size: 100%; }
    ul { margin: 0 0 var(--space-2); padding-inline-start: 1.2rem; }
  `,
})
export class ReceiptActions {
  readonly request = input.required<PurchaseRequest>();
  readonly changed = output<void>();

  private readonly api = inject(ReceiptApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly dossier = signal<ReceiptDossier | null>(null);
  protected readonly note = signal('');
  protected readonly conditions = RECEIPT_CONDITIONS;
  protected readonly drafts = signal<Record<number, ReceiptLineDraft>>({});
  protected readonly shortfallReason = signal('');
  protected readonly photoRef = signal('');
  protected readonly invoiceNo = signal('');
  protected readonly invoiceAmount = signal('');
  protected readonly docRef = signal('');
  protected readonly matchAttested = signal(false);
  protected readonly rating = signal('');
  protected readonly waiver = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly visible = signal(false);

  constructor() {
    effect(() => {
      const status = this.request().status;
      const id = this.request().id;
      untracked(() => {
        const show =
          status === 'DELIVERED' ||
          status === 'PARTIALLY_RECEIVED' ||
          status === 'NON_CONFORMING' ||
          status === 'ACCEPTED' ||
          status === 'INVOICED' ||
          status === 'PAYMENT_PENDING' ||
          status === 'PAID' ||
          status === 'CLOSED';
        this.visible.set(show && (this.canReceive() || this.canConfirm() || this.canInvoice() || this.canClose() || this.isAuthor()));
        if (show) {
          this.reload(id);
        } else {
          this.dossier.set(null);
        }
      });
    });
  }

  protected isAuthor(): boolean {
    const me = this.auth.me();
    const r = this.request();
    return !!me && (me.id === r.requester?.id || me.id === r.enteredBy?.id);
  }

  protected canReceive(): boolean {
    return hasPermission(this.auth.me(), 'RECEIPT_ACT') || this.isAuthor();
  }

  protected canConfirm(): boolean {
    return this.isAuthor() || hasPermission(this.auth.me(), 'RECEIPT_ACT');
  }

  protected canInvoice(): boolean {
    return hasPermission(this.auth.me(), 'PURCHASING_ACT') || hasPermission(this.auth.me(), 'PURCHASING_MANAGE');
  }

  protected canClose(): boolean {
    return hasPermission(this.auth.me(), 'PURCHASING_MANAGE') || this.isAuthor();
  }

  protected canManage(): boolean {
    return hasPermission(this.auth.me(), 'PURCHASING_MANAGE');
  }

  protected openLines() {
    return (this.dossier()?.purchaseOrder?.lines ?? []).filter((line) => Number(line.receivable) > 0);
  }

  /** A line that did not arrive is left out of the receipt: entering 0 means "not in this delivery". */
  protected receiptLines() {
    return this.openLines().filter((line) => Number(this.draft(line.id).received) > 0);
  }

  protected draft(lineId: number): ReceiptLineDraft {
    return this.drafts()[lineId] ?? { received: '', accepted: '', rejected: '0', condition: 'OK' };
  }

  protected setDraft(lineId: number, field: keyof ReceiptLineDraft, value: string): void {
    this.drafts.update((all) => ({ ...all, [lineId]: { ...this.draft(lineId), [field]: value } }));
  }

  protected canAcceptShortfall(d: ReceiptDossier): boolean {
    const status = this.request().status;
    return (
      this.canManage() &&
      (status === 'NON_CONFORMING' || status === 'PARTIALLY_RECEIVED') &&
      d.receipts.length > 0 &&
      d.receipts.every((r) => r.status !== 'RECORDED')
    );
  }

  protected isDeliveredStage(): boolean {
    const s = this.request().status;
    return s === 'DELIVERED' || s === 'PARTIALLY_RECEIVED' || s === 'NON_CONFORMING';
  }

  protected isAcceptedOrLater(): boolean {
    const s = this.request().status;
    return s === 'ACCEPTED' || s === 'INVOICED' || s === 'PAYMENT_PENDING' || s === 'PAID';
  }

  create(): void {
    this.busy.set(true);
    this.error.set(null);
    const evidence = this.photoRef().trim()
      ? [{ kind: 'PHOTO', referenceNumber: this.photoRef().trim() }]
      : [];
    this.api
      .createReceipt(
        this.request().id,
        {
          requestVersion: this.request().version,
          note: this.note().trim() || undefined,
          evidence,
          lines: this.receiptLines().map((line) => ({
            purchaseOrderLineId: line.id,
            quantityReceived: Number(this.draft(line.id).received),
            quantityAccepted: Number(this.draft(line.id).accepted),
            quantityRejected: Number(this.draft(line.id).rejected),
            quantityDamaged: 0,
            condition: this.draft(line.id).condition,
          })),
        },
        newIdempotencyKey(),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('receipt.actions.recorded');
          this.drafts.set({});
          this.changed.emit();
          this.reload(this.request().id);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  confirm(receiptId: number, version: number): void {
    this.busy.set(true);
    this.api.confirm(receiptId, { version }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('receipt.actions.confirmed');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  saveInvoice(): void {
    this.busy.set(true);
    this.api
      .invoice(
        this.request().id,
        {
          requestVersion: this.request().version,
          invoiceNumber: this.invoiceNo().trim(),
          amount: Number(this.invoiceAmount()),
          currency: this.dossier()?.purchaseOrder?.currency ?? this.request().currency,
          documentReference: this.docRef().trim() || undefined,
          matchAttested: this.matchAttested(),
          paymentStatus: 'PENDING',
        },
        newIdempotencyKey(),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('receipt.actions.invoiceSaved');
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  acceptShortfall(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.acceptShortfall(this.request().id, { requestVersion: this.request().version, reason: this.shortfallReason().trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('receipt.actions.shortfallAccepted');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  attest(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.attestMatch(this.request().id, { requestVersion: this.request().version }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('receipt.actions.attestedToast');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  markPaid(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.markPaid(this.request().id, { requestVersion: this.request().version }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('receipt.actions.paidToast');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  close(): void {
    this.busy.set(true);
    this.api
      .close(
        this.request().id,
        {
          requestVersion: this.request().version,
          rating: Number(this.rating()) || undefined,
          waiverNote: this.waiver().trim() || undefined,
        },
        newIdempotencyKey(),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('receipt.actions.closedToast');
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  /** Starts each open line at its full remaining quantity and the invoice at the purchase-order total. */
  private prefill(d: ReceiptDossier): void {
    const next: Record<number, ReceiptLineDraft> = {};
    for (const line of d.purchaseOrder?.lines ?? []) {
      const remaining = String(Number(line.receivable));
      next[line.id] = this.drafts()[line.id] ?? { received: remaining, accepted: remaining, rejected: '0', condition: 'OK' };
    }
    this.drafts.set(next);
    if (!this.invoiceAmount() && d.purchaseOrder) {
      this.invoiceAmount.set(String(d.purchaseOrder.total));
    }
  }

  /** Identifies the newest reload, so a slower, older response cannot overwrite it. */
  private reloadSeq = 0;

  private reload(requestId: number): void {
    const seq = ++this.reloadSeq;
    this.api.dossier(requestId).subscribe({
      next: (d) => {
        if (seq === this.reloadSeq) {
          this.dossier.set(d);
          this.prefill(d);
        }
      },
      error: () => {
        if (seq === this.reloadSeq) {
          this.dossier.set(null);
        }
      },
    });
  }
}
