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
import { ReceiptDossier } from './receipt.models';

@Component({
  selector: 'app-receipt-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel decide" aria-labelledby="receipt-title">
        <h2 class="panel-title" id="receipt-title">{{ 'receipt.actions.title' | translate }}</h2>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
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

          @if (canReceive() && d.receipts.length === 0 && isDeliveredStage()) {
            <form class="form" (submit)="$event.preventDefault(); create()" novalidate>
              <label class="label" for="rcpt-note">{{ 'receipt.actions.note' | translate }}</label>
              <input id="rcpt-note" [value]="note()" (input)="note.set($any($event.target).value)" />
              <label class="label" for="rcpt-photo">{{ 'receipt.actions.photoRef' | translate }}</label>
              <input id="rcpt-photo" [value]="photoRef()" (input)="photoRef.set($any($event.target).value)" />
              <p class="muted small">{{ 'receipt.actions.photoHint' | translate }}</p>
              <button appButton type="submit" [disabled]="busy()">{{ 'receipt.actions.record' | translate }}</button>
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
          } @else if (canInvoice() && isAcceptedOrLater()) {
            <form class="form" (submit)="$event.preventDefault(); saveInvoice()" novalidate>
              <label class="label" for="inv-no">{{ 'receipt.actions.invoiceNumber' | translate }}</label>
              <input id="inv-no" [value]="invoiceNo()" (input)="invoiceNo.set($any($event.target).value)" />
              <label class="label" for="inv-amt">{{ 'receipt.actions.invoiceAmount' | translate }}</label>
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
                <p class="muted small">{{ 'receipt.actions.closureMissing' | translate }}: {{ d.closure.missing.join(', ') }}</p>
              }
              <label class="label" for="score">{{ 'receipt.actions.rating' | translate }}</label>
              <input id="score" type="number" min="1" max="5" [value]="rating()" (input)="rating.set($any($event.target).value)" />
              <label class="label" for="waiver">{{ 'receipt.actions.waiver' | translate }}</label>
              <input id="waiver" [value]="waiver()" (input)="waiver.set($any($event.target).value)" />
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
  protected readonly photoRef = signal('CAM-DEMO-1');
  protected readonly invoiceNo = signal('INV-DEMO-1');
  protected readonly invoiceAmount = signal('35');
  protected readonly docRef = signal('PDF-REF-1');
  protected readonly matchAttested = signal(true);
  protected readonly rating = signal('5');
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
      ? [{ kind: 'PHOTO', referenceNumber: this.photoRef().trim(), note: 'Metadata reference only' }]
      : [];
    this.api
      .createReceipt(
        this.request().id,
        { requestVersion: this.request().version, note: this.note().trim() || undefined, evidence },
        newIdempotencyKey(),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('receipt.actions.recorded');
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
          currency: this.request().currency || 'EUR',
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

  close(): void {
    this.busy.set(true);
    this.api
      .close(
        this.request().id,
        {
          requestVersion: this.request().version,
          rating: Number(this.rating()) || undefined,
          scoreComment: 'Demo score',
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

  private reload(requestId: number): void {
    this.api.dossier(requestId).subscribe({
      next: (d) => this.dossier.set(d),
      error: () => this.dossier.set(null),
    });
  }
}
