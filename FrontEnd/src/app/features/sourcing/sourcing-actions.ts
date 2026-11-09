import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { PurchaseRequest } from '../requests/request.models';
import { SuppliersApi } from '../suppliers/suppliers.api';
import { ConversationPanel } from '../messaging/conversation-panel';
import { ComparisonView, RfqDetail } from './sourcing.models';
import { SourcingApi } from './sourcing.api';

/**
 * RFQ creation, manual quote, comparison and technical/commercial validation on the request dossier (Sprint 4).
 */
@Component({
  selector: 'app-sourcing-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, ConversationPanel, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel decide" aria-labelledby="sourcing-title">
        <h2 class="panel-title" id="sourcing-title">{{ 'sourcing.actions.title' | translate }}</h2>

        @if (!rfq()) {
          <form class="form" (submit)="$event.preventDefault(); create()" novalidate>
            <label class="label" for="rfq-kind">{{ 'sourcing.actions.kind' | translate }} *</label>
            <select id="rfq-kind" [value]="kind()" (change)="kind.set($any($event.target).value)">
              <option value="QUICK">{{ 'sourcing.kind.QUICK' | translate }}</option>
              <option value="STANDARD">{{ 'sourcing.kind.STANDARD' | translate }}</option>
            </select>
            <label class="label" for="rfq-supplier">{{ 'sourcing.actions.supplier' | translate }} *</label>
            <select id="rfq-supplier" [value]="supplierId() ?? ''" (change)="supplierId.set(toId($any($event.target).value))">
              <option value="">{{ 'requests.form.choose' | translate }}</option>
              @for (s of suppliers(); track s.id) {
                <option [value]="s.id">{{ s.legalName }} ({{ s.code }})</option>
              }
            </select>
            <p class="error small" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>
            <button appButton type="submit" [disabled]="busy() || !supplierId()">{{ 'sourcing.actions.create' | translate }}</button>
          </form>
        } @else {
          <p class="muted small">{{ 'sourcing.kind.' + rfq()!.kind | translate }} · {{ 'sourcing.status.' + rfq()!.status | translate }}</p>

          @if (rfq()!.status === 'OPEN' && canAct()) {
            <form class="form" (submit)="$event.preventDefault(); submitQuote()" novalidate>
              <label class="label" for="quote-price">{{ 'sourcing.actions.unitPrice' | translate }} ({{ rfq()!.comparisonCurrency }}) *</label>
              <input id="quote-price" type="number" min="0" step="0.01" [value]="unitPrice()" (input)="unitPrice.set($any($event.target).value)" />
              <button appButton type="submit" [disabled]="busy()">{{ 'sourcing.actions.submitQuote' | translate }}</button>
            </form>
            <button appButton variant="secondary" type="button" [disabled]="busy()" (click)="copyLink()">
              {{ 'sourcing.actions.copyLink' | translate }}
            </button>
          }

          @if (comparison(); as cmp) {
            <h3 class="history-title">{{ 'sourcing.actions.comparison' | translate }} ({{ cmp.comparisonCurrency }})</h3>
            <div class="table-wrap">
              <table class="compare">
                <thead>
                  <tr>
                    <th>{{ 'sourcing.actions.supplier' | translate }}</th>
                    <th>{{ 'sourcing.actions.landed' | translate }}</th>
                    <th>{{ 'sourcing.actions.missing' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of cmp.rows; track row.quotationId) {
                    <tr>
                      <td>{{ row.supplierName }}</td>
                      <td class="tabular">{{ row.landedInComparisonCurrency }}</td>
                      <td>{{ row.missingFields.length ? row.missingFields.join(', ') : '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }

          @if (canValidateTechnical()) {
            <form class="form" (submit)="$event.preventDefault(); validate('technical')" novalidate>
              <label class="label" for="tech-comment">{{ 'sourcing.actions.techComment' | translate }} *</label>
              <textarea id="tech-comment" rows="2" maxlength="2000" [value]="comment()" (input)="comment.set($any($event.target).value)"></textarea>
              <button appButton type="submit" [disabled]="busy()">{{ 'sourcing.actions.validateTechnical' | translate }}</button>
            </form>
          }
          @if (canValidateCommercial()) {
            <form class="form" (submit)="$event.preventDefault(); validate('commercial')" novalidate>
              <label class="label" for="comm-comment">{{ 'sourcing.actions.commComment' | translate }} *</label>
              <textarea id="comm-comment" rows="2" maxlength="2000" [value]="comment()" (input)="comment.set($any($event.target).value)"></textarea>
              <button appButton type="submit" [disabled]="busy()">{{ 'sourcing.actions.validateCommercial' | translate }}</button>
            </form>
          }
          <p class="error small" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>

          @if (rfq(); as r) {
            @if (r.suppliers[0]; as s) {
              <app-conversation-panel contextType="RFQ" [contextId]="r.id" [supplierId]="s.supplierId" />
            }
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
    .history-title { margin-block-start: var(--space-3); font-size: var(--text-sm); }
    .table-wrap { overflow-x: auto; }
    .compare { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
    .compare th, .compare td { padding: var(--space-2); border-block-end: 1px solid var(--color-line); text-align: start; }
  `,
})
export class SourcingActions {
  readonly request = input.required<PurchaseRequest>();
  readonly changed = output<void>();

  private readonly api = inject(SourcingApi);
  private readonly suppliersApi = inject(SuppliersApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly rfq = signal<RfqDetail | null>(null);
  protected readonly comparison = signal<ComparisonView | null>(null);
  protected readonly suppliers = signal<{ id: number; code: string; legalName: string }[]>([]);
  protected readonly kind = signal<'QUICK' | 'STANDARD'>('QUICK');
  protected readonly supplierId = signal<number | null>(null);
  protected readonly unitPrice = signal('25');
  protected readonly comment = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly visible = computed(() => {
    const status = this.request().status;
    return (
      this.canAct() &&
      (status === 'ASSIGNED_TO_BUYER' ||
        status === 'SOURCING' ||
        status === 'QUOTES_RECEIVED' ||
        status === 'TECHNICAL_VALIDATION' ||
        status === 'COMMERCIAL_VALIDATION')
    );
  });

  constructor() {
    effect(() => {
      const id = this.request().id;
      const status = this.request().status;
      untracked(() => {
        if (this.canAct() && status === 'ASSIGNED_TO_BUYER') {
          this.loadSuppliers();
        }
        if (
          status === 'SOURCING' ||
          status === 'QUOTES_RECEIVED' ||
          status === 'TECHNICAL_VALIDATION' ||
          status === 'COMMERCIAL_VALIDATION'
        ) {
          this.loadRfq(id);
        } else {
          this.rfq.set(null);
          this.comparison.set(null);
        }
      });
    });
  }

  protected canAct(): boolean {
    return hasPermission(this.auth.me(), 'PURCHASING_ACT') || hasPermission(this.auth.me(), 'PURCHASING_MANAGE');
  }

  protected canValidateTechnical(): boolean {
    return this.request().status === 'QUOTES_RECEIVED' && this.canAct();
  }

  protected canValidateCommercial(): boolean {
    return this.request().status === 'TECHNICAL_VALIDATION' && this.canAct();
  }

  protected toId(value: string): number | null {
    const id = Number(value);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  create(): void {
    if (!this.supplierId()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.api.createRfq(this.request().id, { kind: this.kind(), supplierIds: [this.supplierId()!] }, newIdempotencyKey()).subscribe({
      next: (rfq) => {
        this.busy.set(false);
        this.reloadSeq++; // This response is newer than any load still in flight.
        this.rfq.set(rfq);
        this.notifications.success('sourcing.actions.created');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = toApiProblem(error);
        if (problem.status !== 401) {
          this.error.set(problem.code);
        }
      },
    });
  }

  submitQuote(): void {
    const rfq = this.rfq();
    if (!rfq?.suppliers[0] || !rfq.lines[0]) {
      return;
    }
    const price = Number(this.unitPrice());
    this.busy.set(true);
    this.api
      .submitQuote(
        rfq.id,
        {
          rfqSupplierId: rfq.suppliers[0].id,
          currency: rfq.comparisonCurrency,
          freightAmount: 0,
          dutyAmount: 0,
          otherAmount: 0,
          lines: [{ rfqLineId: rfq.lines[0].id, quantity: Number(rfq.lines[0].quantity), unitPrice: Number.isFinite(price) ? price : null }],
        },
        newIdempotencyKey(),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('sourcing.actions.quoteSaved');
          this.loadRfq(this.request().id);
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  copyLink(): void {
    const rfq = this.rfq();
    if (!rfq?.suppliers[0]) {
      return;
    }
    this.busy.set(true);
    this.api.issueLink(rfq.id, rfq.suppliers[0].id, newIdempotencyKey()).subscribe({
      next: (link) => {
        this.busy.set(false);
        const url = `${window.location.origin}${link.path}`;
        void navigator.clipboard.writeText(url);
        this.notifications.success('sourcing.actions.linkCopied');
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  validate(kind: 'technical' | 'commercial'): void {
    if (!this.comment().trim()) {
      this.error.set('validation-failed');
      return;
    }
    this.busy.set(true);
    const call =
      kind === 'technical'
        ? this.api.validateTechnical(this.request().id, this.request().version, this.comment().trim(), newIdempotencyKey())
        : this.api.validateCommercial(this.request().id, this.request().version, this.comment().trim(), newIdempotencyKey());
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.comment.set('');
        this.notifications.success(kind === 'technical' ? 'sourcing.actions.techDone' : 'sourcing.actions.commDone');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  private loadSuppliers(): void {
    this.suppliersApi.list(null, null, 0, 50).subscribe({
      next: (page) => this.suppliers.set(page.items.map((s) => ({ id: s.id, code: s.code, legalName: s.legalName }))),
      error: () => this.suppliers.set([]),
    });
  }

  /** Identifies the newest load, so a slower, older response cannot overwrite it. */
  private reloadSeq = 0;

  private loadRfq(requestId: number): void {
    const seq = ++this.reloadSeq;
    this.api.getRfq(requestId).subscribe({
      next: (rfq) => {
        if (seq !== this.reloadSeq) {
          return;
        }
        this.rfq.set(rfq);
        if (!rfq) {
          this.comparison.set(null);
          return;
        }
        this.api.comparison(rfq.id).subscribe({
          next: (cmp) => {
            if (seq === this.reloadSeq) {
              this.comparison.set(cmp);
            }
          },
          error: () => {
            if (seq === this.reloadSeq) {
              this.comparison.set(null);
            }
          },
        });
      },
      error: () => {
        if (seq !== this.reloadSeq) {
          return;
        }
        this.rfq.set(null);
        this.comparison.set(null);
      },
    });
  }
}
