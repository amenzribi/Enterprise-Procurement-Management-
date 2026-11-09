import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { AuthService } from '../../core/auth/auth.service';
import { hasPermission } from '../../core/auth/auth.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { PurchaseRequest } from '../requests/request.models';
import { RfqDetail } from '../sourcing/sourcing.models';
import { SourcingApi } from '../sourcing/sourcing.api';
import { OrderingApi } from './ordering.api';
import { AwardView, PurchaseOrderView } from './ordering.models';

@Component({
  selector: 'app-ordering-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel decide" aria-labelledby="ordering-title">
        <h2 class="panel-title" id="ordering-title">{{ 'ordering.actions.title' | translate }}</h2>
        @if (error()) {
          <p class="error" role="alert">{{ 'errors.' + error() | translate }}</p>
        }

        @if (!award() && canAward()) {
          <form class="form" (submit)="$event.preventDefault(); awardSupplier()" novalidate>
            <label class="label" for="award-quote">{{ 'ordering.actions.quotation' | translate }} *</label>
            <select id="award-quote" [value]="quotationId() ?? ''" (change)="quotationId.set(toId($any($event.target).value))">
              <option value="">{{ 'ordering.actions.chooseQuote' | translate }}</option>
              @for (q of submittedQuotes(); track q.id) {
                <option [value]="q.id" [disabled]="q.expired">
                  {{ q.supplierName }} — {{ q.currency }} {{ q.merchandiseAmount }}
                  @if (q.expired) {
                    ({{ 'ordering.actions.quoteExpired' | translate }})
                  }
                </option>
              }
            </select>
            <label class="label" for="award-reason">{{ 'ordering.actions.reason' | translate }} *</label>
            <textarea id="award-reason" rows="3" [value]="reason()" (input)="reason.set($any($event.target).value)"></textarea>
            @if (selectedQuantityDiffers()) {
              <label class="label" for="award-quantity-reason">{{ 'ordering.actions.quantityReason' | translate }} *</label>
              <textarea id="award-quantity-reason" rows="2" maxlength="500" [value]="quantityReason()" (input)="quantityReason.set($any($event.target).value)"></textarea>
            }
            <button appButton type="submit" [disabled]="busy() || !quotationId() || reason().trim().length < 3 || (selectedQuantityDiffers() && quantityReason().trim().length < 3)">
              {{ 'ordering.actions.award' | translate }}
            </button>
          </form>
        }

        @if (award(); as a) {
          <p class="muted small">
            {{ a.supplierName }} · {{ a.currency }} {{ a.landedAmount }} · {{ 'ordering.actions.awardedAt' | translate }}
          </p>
          <p class="small">{{ a.reason }}</p>
        }

        @if (award() && !po() && canAct()) {
          <form class="form" (submit)="$event.preventDefault(); createPo()" novalidate>
            <label class="label" for="po-terms">{{ 'ordering.actions.paymentTerms' | translate }}</label>
            <input id="po-terms" [value]="paymentTerms()" (input)="paymentTerms.set($any($event.target).value)" />
            <label class="label" for="po-incoterm">{{ 'ordering.actions.incoterm' | translate }}</label>
            <input id="po-incoterm" [value]="incoterm()" (input)="incoterm.set($any($event.target).value)" />
            <label class="label" for="po-delivery">{{ 'ordering.actions.deliveryAddress' | translate }}</label>
            <input id="po-delivery" [value]="deliveryAddress()" (input)="deliveryAddress.set($any($event.target).value)" />
            <button appButton type="submit" [disabled]="busy()">{{ 'ordering.actions.createPo' | translate }}</button>
          </form>
        }

        @if (po(); as p) {
          <p class="muted small">
            {{ p.number }} · {{ 'ordering.status.' + p.status | translate }} · {{ p.currency }} {{ p.totalAmount }}
          </p>
          @if (p.status === 'DRAFT' && canAct()) {
            <button appButton type="button" [disabled]="busy()" (click)="issue()">{{ 'ordering.actions.issue' | translate }}</button>
          }
          @if (p.status === 'ISSUED' && canAct()) {
            <form class="form" (submit)="$event.preventDefault(); confirm()" novalidate>
              <label class="label" for="po-confirm-note">{{ 'ordering.actions.confirmationNote' | translate }}</label>
              <input id="po-confirm-note" [value]="confirmationNote()" (input)="confirmationNote.set($any($event.target).value)" />
              <button appButton type="submit" [disabled]="busy()">{{ 'ordering.actions.confirm' | translate }}</button>
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
    select, input, textarea {
      min-block-size: 2.5rem;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
    }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
    .error { color: var(--color-danger); }
  `,
})
export class OrderingActions {
  readonly request = input.required<PurchaseRequest>();
  readonly changed = output<void>();

  private readonly api = inject(OrderingApi);
  private readonly sourcingApi = inject(SourcingApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly award = signal<AwardView | null>(null);
  protected readonly po = signal<PurchaseOrderView | null>(null);
  protected readonly submittedQuotes = signal<{ id: number; supplierName: string; currency: string; merchandiseAmount: number; expired: boolean; quantityDiffers: boolean }[]>([]);
  protected readonly quotationId = signal<number | null>(null);
  protected readonly reason = signal('');
  protected readonly quantityReason = signal('');
  protected readonly paymentTerms = signal('Net 30');
  protected readonly incoterm = signal('DAP');
  protected readonly deliveryAddress = signal('Plant warehouse');
  protected readonly confirmationNote = signal('Confirmed by supplier (demo)');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly visible = signal(false);

  constructor() {
    effect(() => {
      const status = this.request().status;
      const id = this.request().id;
      untracked(() => {
        if (this.awaitingRequest) {
          this.awaitingRequest = false;
          this.busy.set(false);
        }
        const show =
          status === 'COMMERCIAL_VALIDATION' ||
          status === 'MANAGEMENT_VALIDATION' ||
          status === 'AWARDED' ||
          status === 'ORDERED';
        this.visible.set(show && (this.canAct() || this.canViewAsAuthor()));
        if (show) {
          this.reload(id, status);
        } else {
          this.award.set(null);
          this.po.set(null);
        }
      });
    });
  }

  protected canAct(): boolean {
    return hasPermission(this.auth.me(), 'PURCHASING_ACT') || hasPermission(this.auth.me(), 'PURCHASING_MANAGE');
  }

  protected canViewAsAuthor(): boolean {
    const me = this.auth.me();
    const r = this.request();
    return !!me && (me.id === r.requester?.id || me.id === r.enteredBy?.id);
  }

  protected canAward(): boolean {
    return this.canAct() && (this.request().status === 'COMMERCIAL_VALIDATION' || this.request().status === 'MANAGEMENT_VALIDATION');
  }

  protected selectedQuantityDiffers(): boolean {
    return this.submittedQuotes().find((q) => q.id === this.quotationId())?.quantityDiffers ?? false;
  }

  protected toId(value: string): number | null {
    const id = Number(value);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  awardSupplier(): void {
    if (!this.quotationId() || this.reason().trim().length < 3) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api
      .award(
        this.request().id,
        { requestVersion: this.request().version, quotationId: this.quotationId()!, quantityReason: this.selectedQuantityDiffers() ? this.quantityReason().trim() : undefined, reason: this.reason().trim() },
        newIdempotencyKey(),
      )
      .subscribe({
        next: (award) => {
          // Stay busy until the parent delivers the new request version: the next action must send it.
          this.awaitingRequest = true;
          this.reloadSeq++;
          this.award.set(award);
          this.notifications.success('ordering.actions.awarded');
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  createPo(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .createPo(
        this.request().id,
        {
          requestVersion: this.request().version,
          paymentTerms: this.paymentTerms().trim() || null,
          incoterm: this.incoterm().trim() || null,
          deliveryAddress: this.deliveryAddress().trim() || null,
          requiredDate: this.request().requiredDate,
        },
        newIdempotencyKey(),
      )
      .subscribe({
        next: (po) => {
          this.busy.set(false);
          this.reloadSeq++;
          this.po.set(po);
          this.notifications.success('ordering.actions.poCreated');
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  issue(): void {
    const po = this.po();
    if (!po) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.issue(po.id, po.version, newIdempotencyKey()).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.reloadSeq++;
        this.po.set(updated);
        this.notifications.success('ordering.actions.issued');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  confirm(): void {
    const po = this.po();
    if (!po) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.confirm(po.id, po.version, this.confirmationNote().trim(), newIdempotencyKey()).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.reloadSeq++;
        this.po.set(updated);
        this.notifications.success('ordering.actions.confirmed');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  private awaitingRequest = false;

  /** Identifies the newest reload, so a slower, older response cannot overwrite it. */
  private reloadSeq = 0;

  private reload(requestId: number, status: string): void {
    const seq = ++this.reloadSeq;
    this.api.getAward(requestId).subscribe({
      next: (award) => {
        if (seq !== this.reloadSeq) {
          return;
        }
        this.award.set(award);
        this.api.getPo(requestId).subscribe({
          next: (po) => {
            if (seq === this.reloadSeq) {
              this.po.set(po);
            }
          },
          error: () => {
            if (seq === this.reloadSeq) {
              this.po.set(null);
            }
          },
        });
      },
      error: () => {
        if (seq !== this.reloadSeq) {
          return;
        }
        this.award.set(null);
        this.po.set(null);
        if (status === 'COMMERCIAL_VALIDATION' || status === 'MANAGEMENT_VALIDATION') {
          this.loadQuotes(requestId);
        }
      },
    });
  }

  private loadQuotes(requestId: number): void {
    this.sourcingApi.getRfq(requestId).subscribe({
      next: (rfq: RfqDetail | null) => {
        const today = new Date().toISOString().slice(0, 10);
        const quotes = (rfq?.quotes ?? [])
          .filter((q) => q.status === 'SUBMITTED')
          .map((q) => ({
            id: q.id,
            supplierName: q.supplierName,
            currency: q.currency,
            merchandiseAmount: Number(q.merchandiseAmount ?? 0),
            expired: !!q.validUntil && q.validUntil < today,
            quantityDiffers: q.lines.some((line) => {
              const requested = rfq?.lines.find((l) => l.id === line.rfqLineId);
              return !!requested && Number(requested.quantity) !== Number(line.quantity);
            }),
          }));
        this.submittedQuotes.set(quotes);
        const eligible = quotes.filter((q) => !q.expired);
        if (eligible.length === 1) {
          this.quotationId.set(eligible[0].id);
        }
      },
      error: () => this.submittedQuotes.set([]),
    });
  }
}
