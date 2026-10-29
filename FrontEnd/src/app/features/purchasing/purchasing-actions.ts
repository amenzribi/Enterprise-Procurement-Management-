import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { StatusBadge } from '../../shared/ui/status-badge';
import { PurchaseRequest } from '../requests/request.models';
import { PurchasingCountService } from './purchasing-count.service';
import { Assignment, BuyerOption, PURCHASE_MODES } from './purchasing.models';
import { PurchasingApi } from './purchasing.api';

type PanelAction = 'assign' | 'mode' | 'return-assignment' | 'completeness' | null;

/**
 * Purchasing actions on a request dossier: assign/accept/return, purchase mode, completeness return (S3-AC-02..04).
 */
@Component({
  selector: 'app-purchasing-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, Icon, StatusBadge, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel decide" aria-labelledby="purchasing-title">
        <h2 class="panel-title" id="purchasing-title">{{ 'purchasing.actions.title' | translate }}</h2>
        <dl class="facts">
          <div>
            <dt>{{ 'purchasing.actions.buyer' | translate }}</dt>
            <dd>{{ request().assignedBuyer?.displayName ?? ('purchasing.inbox.unassigned' | translate) }}</dd>
          </div>
          <div>
            <dt>{{ 'purchasing.inbox.mode' | translate }}</dt>
            <dd>
              @if (request().purchaseMode) {
                {{ 'purchasing.mode.' + request().purchaseMode | translate }}
              } @else {
                —
              }
              @if (request().buyingCompanyProposed) {
                <span class="muted"> ({{ 'purchasing.inbox.proposed' | translate }})</span>
              }
            </dd>
          </div>
        </dl>
        @if (gaps().length) {
          <p class="muted small">{{ 'purchasing.inbox.gaps' | translate: { list: gaps().join(', ') } }}</p>
        } @else {
          <p class="muted small">{{ 'purchasing.actions.complete' | translate }}</p>
        }

        @if (selected() === null) {
          <div class="choices">
            @if (canManage()) {
              <button appButton type="button" (click)="choose('assign')">
                <app-icon name="user" [size]="16" />
                {{ 'purchasing.actions.assign' | translate }}
              </button>
              <button appButton variant="secondary" type="button" (click)="choose('mode')">
                {{ 'purchasing.actions.setMode' | translate }}
              </button>
              <button appButton variant="secondary" type="button" (click)="choose('completeness')">
                {{ 'purchasing.actions.returnDossier' | translate }}
              </button>
            }
            @if (canAccept()) {
              <button appButton type="button" (click)="run('accept')">
                <app-icon name="circle-check" [size]="16" />
                {{ 'purchasing.actions.accept' | translate }}
              </button>
              <button appButton variant="secondary" type="button" (click)="choose('return-assignment')">
                {{ 'purchasing.actions.returnAssignment' | translate }}
              </button>
            }
          </div>
        } @else if (selected() === 'assign') {
          <form class="form" (submit)="$event.preventDefault(); run('assign')" novalidate>
            <label class="label" for="purchase-buyer">{{ 'purchasing.actions.buyer' | translate }} *</label>
            @if (buyers().length === 0) {
              <p class="muted small">{{ 'purchasing.actions.noBuyers' | translate }}</p>
            } @else {
              <select id="purchase-buyer" [value]="buyerId() ?? ''" (change)="buyerId.set(toId($any($event.target).value))">
                <option value="">{{ 'requests.form.choose' | translate }}</option>
                @for (buyer of buyers(); track buyer.id) {
                  <option [value]="buyer.id">
                    {{ buyer.displayName }}
                    @if (buyer.suggested) {
                      ({{ 'purchasing.actions.suggested' | translate }})
                    }
                    — {{ 'purchasing.actions.openLoad' | translate: { count: buyer.openAssignments } }}
                  </option>
                }
              </select>
            }
            <p class="error small" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>
            <div class="choices">
              <button appButton type="submit" [disabled]="busy() || !buyerId()">{{ 'purchasing.actions.assignConfirm' | translate }}</button>
              <button appButton variant="secondary" type="button" (click)="cancel()">{{ 'common.cancel' | translate }}</button>
            </div>
          </form>
        } @else if (selected() === 'mode') {
          <form class="form" (submit)="$event.preventDefault(); run('mode')" novalidate>
            <label class="label" for="purchase-mode">{{ 'purchasing.actions.mode' | translate }} *</label>
            <select id="purchase-mode" [value]="mode() ?? ''" (change)="mode.set($any($event.target).value || null)">
              <option value="">{{ 'requests.form.choose' | translate }}</option>
              @for (option of modes; track option) {
                <option [value]="option">{{ 'purchasing.mode.' + option | translate }}</option>
              }
            </select>
            <p class="muted small">{{ 'purchasing.actions.chargedUnchanged' | translate }}</p>
            <p class="error small" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>
            <div class="choices">
              <button appButton type="submit" [disabled]="busy() || !mode()">{{ 'purchasing.actions.setMode' | translate }}</button>
              <button appButton variant="secondary" type="button" (click)="cancel()">{{ 'common.cancel' | translate }}</button>
            </div>
          </form>
        } @else {
          <form class="form" (submit)="$event.preventDefault(); run(selected() === 'completeness' ? 'completeness' : 'return-assignment')" novalidate>
            <label class="label" for="purchase-reason">{{ 'purchasing.actions.reason' | translate }} *</label>
            <textarea id="purchase-reason" rows="3" maxlength="2000" [value]="reason()" (input)="reason.set($any($event.target).value)"></textarea>
            <p class="error small" role="alert">
              @if (showErrors() && !reason().trim()) {
                {{ 'purchasing.actions.reasonRequired' | translate }}
              } @else if (error(); as code) {
                {{ 'errors.' + code | translate }}
              }
            </p>
            <div class="choices">
              <button appButton type="submit" [disabled]="busy()">
                {{ (selected() === 'completeness' ? 'purchasing.actions.returnDossier' : 'purchasing.actions.returnAssignment') | translate }}
              </button>
              <button appButton variant="secondary" type="button" (click)="cancel()">{{ 'common.cancel' | translate }}</button>
            </div>
          </form>
        }

        @if (history().length) {
          <h3 class="history-title">{{ 'purchasing.actions.history' | translate }}</h3>
          <ol class="history">
            @for (row of history(); track row.id) {
              <li>
                <app-status-badge [tone]="row.status === 'ACCEPTED' ? 'success' : row.status === 'RETURNED' ? 'warning' : 'neutral'">
                  {{ 'purchasing.assignmentStatus.' + row.status | translate }}
                </app-status-badge>
                {{ row.buyerName }}
                <time [attr.datetime]="row.assignedAt">{{ row.assignedAt | date: 'medium' }}</time>
                @if (row.reason) {
                  — {{ row.reason }}
                }
              </li>
            }
          </ol>
        }
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .facts { display: grid; gap: var(--space-2); margin: 0 0 var(--space-3); }
    .facts div { display: grid; gap: 0.125rem; }
    dt { color: var(--color-text-muted); font-size: var(--text-sm); }
    .choices { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .form { display: grid; gap: var(--space-2); }
    .label { font-weight: var(--weight-medium); font-size: var(--text-sm); }
    select, textarea {
      min-block-size: 2.5rem;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
    }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
    .error { color: var(--color-danger); min-block-size: 1.25rem; }
    .history-title { margin-block-start: var(--space-4); font-size: var(--text-sm); }
    .history { margin: var(--space-2) 0 0; padding-inline-start: 1.25rem; display: grid; gap: var(--space-2); font-size: var(--text-sm); }
  `,
})
export class PurchasingActions {
  readonly request = input.required<PurchaseRequest>();
  readonly changed = output<void>();

  private readonly api = inject(PurchasingApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly count = inject(PurchasingCountService);

  protected readonly modes = PURCHASE_MODES;
  protected readonly selected = signal<PanelAction>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showErrors = signal(false);
  protected readonly buyers = signal<BuyerOption[]>([]);
  protected readonly history = signal<Assignment[]>([]);
  protected readonly buyerId = signal<number | null>(null);
  protected readonly mode = signal<string | null>(null);
  protected readonly reason = signal('');
  private pendingKey: string | null = null;

  protected readonly visible = computed(() => {
    const status = this.request().status;
    return (
      this.canAct() &&
      (status === 'IN_PURCHASING_REVIEW' || status === 'ASSIGNED_TO_BUYER' || status === 'PURCHASING_RETURNED')
    );
  });

  protected readonly canManage = computed(() => hasPermission(this.auth.me(), 'PURCHASING_MANAGE') && this.inPurchasing());

  protected readonly canAccept = computed(() => {
    const me = this.auth.me();
    const pending = this.history().find((row) => row.status === 'ASSIGNED');
    return !!me && !!pending && pending.buyerUserId === me.id && this.request().status === 'ASSIGNED_TO_BUYER';
  });

  protected readonly gaps = computed(() => {
    const request = this.request();
    const missing: string[] = [];
    if (!request.title?.trim()) {
      missing.push('title');
    }
    if (!request.category) {
      missing.push('category');
    }
    if (!request.requiredDate) {
      missing.push('requiredDate');
    }
    if (!request.lines?.length) {
      missing.push('lines');
    }
    return missing;
  });

  constructor() {
    effect(() => {
      const key = `${this.request().id}:${this.request().version}`;
      untracked(() => {
        if (key) {
          this.loadHistory();
        }
      });
    });
  }

  protected canAct(): boolean {
    return hasPermission(this.auth.me(), 'PURCHASING_ACT') || hasPermission(this.auth.me(), 'PURCHASING_MANAGE');
  }

  private inPurchasing(): boolean {
    const status = this.request().status;
    return status === 'IN_PURCHASING_REVIEW' || status === 'ASSIGNED_TO_BUYER';
  }

  protected toId(value: string): number | null {
    const id = Number(value);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  protected choose(action: PanelAction): void {
    this.selected.set(action);
    this.error.set(null);
    this.showErrors.set(false);
    this.reason.set('');
    this.pendingKey = null;
    if (action === 'assign') {
      this.loadBuyers();
    }
    this.loadHistory();
  }

  protected cancel(): void {
    this.selected.set(null);
    this.error.set(null);
    this.showErrors.set(false);
  }

  protected run(kind: 'assign' | 'accept' | 'return-assignment' | 'mode' | 'completeness'): void {
    const request = this.request();
    if ((kind === 'return-assignment' || kind === 'completeness') && !this.reason().trim()) {
      this.showErrors.set(true);
      return;
    }
    if (kind === 'assign' && !this.buyerId()) {
      this.showErrors.set(true);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.pendingKey ??= newIdempotencyKey();
    const key = this.pendingKey;
    const body = {
      version: request.version,
      buyerUserId: this.buyerId(),
      purchaseMode: this.mode(),
      reason: this.reason().trim() || null,
    };
    const call =
      kind === 'assign'
        ? this.api.assign(request.id, body, key)
        : kind === 'accept'
          ? this.api.accept(request.id, { version: request.version }, key)
          : kind === 'mode'
            ? this.api.purchaseMode(request.id, body, key)
            : kind === 'completeness'
              ? this.api.completenessReturn(request.id, body, key)
              : this.api.returnAssignment(request.id, body, key);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.pendingKey = null;
        this.selected.set(null);
        this.count.refresh();
        const done =
          kind === 'assign'
            ? 'purchasing.actions.assigned'
            : kind === 'accept'
              ? 'purchasing.actions.accepted'
              : kind === 'mode'
                ? 'purchasing.actions.modeSet'
                : kind === 'completeness'
                  ? 'purchasing.actions.dossierReturned'
                  : 'purchasing.actions.returned';
        this.notifications.success(done, { name: this.buyers().find((b) => b.id === this.buyerId())?.displayName ?? '' });
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = toApiProblem(error);
        if (problem.status === 401) {
          return;
        }
        this.error.set(problem.code);
      },
    });
  }

  private loadBuyers(): void {
    const companyId = this.request().buyingCompany?.id;
    if (!companyId) {
      this.buyers.set([]);
      return;
    }
    this.api.buyers(companyId, this.request().category?.id).subscribe({
      next: (buyers) => {
        this.buyers.set(buyers);
        const suggested = buyers.find((buyer) => buyer.suggested);
        if (suggested && this.buyerId() == null) {
          this.buyerId.set(suggested.id);
        }
      },
      error: () => this.buyers.set([]),
    });
  }

  loadHistory(): void {
    this.api.assignments(this.request().id).subscribe({
      next: (rows) => this.history.set(rows),
      error: () => this.history.set([]),
    });
  }
}
