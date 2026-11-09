import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { AuthService } from '../../core/auth/auth.service';
import { hasPermission } from '../../core/auth/auth.models';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { OrderingApi } from '../ordering/ordering.api';
import { PurchaseRequest } from '../requests/request.models';
import { LogisticsApi } from './logistics.api';
import { ShipmentView } from './logistics.models';

@Component({
  selector: 'app-logistics-actions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel decide" aria-labelledby="logistics-title">
        <h2 class="panel-title" id="logistics-title">{{ 'logistics.actions.title' | translate }}</h2>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }

        <p class="muted small refs">
          <span
            >{{ 'logistics.inbox.requestRef' | translate }}:
            <bdi class="tabular">{{ request().number || ('requests.list.draftNumber' | translate) }}</bdi></span
          >
          @if (poNumber()) {
            <span
              >{{ 'logistics.inbox.poNumber' | translate }}: <bdi class="tabular">{{ poNumber() }}</bdi></span
            >
          }
        </p>

        @if (!canAct() && !canViewAsAuthor()) {
          <p class="muted">{{ 'logistics.actions.noPermission' | translate }}</p>
        } @else if (canAct() && !poConfirmed()) {
          <p class="muted" role="status">{{ 'logistics.actions.needConfirmedPo' | translate }}</p>
        } @else if (canCreate()) {
          <p class="muted small">{{ 'logistics.actions.partialNote' | translate }}</p>
          <form class="form" (submit)="$event.preventDefault(); create()" novalidate>
            <label class="label" for="ship-kind">{{ 'logistics.actions.kind' | translate }}</label>
            <select id="ship-kind" [value]="kind()" (change)="kind.set($any($event.target).value)">
              <option value="LOCAL">{{ 'logistics.kind.LOCAL' | translate }}</option>
              <option value="INTERNATIONAL">{{ 'logistics.kind.INTERNATIONAL' | translate }}</option>
            </select>
            <label class="label" for="ship-carrier">{{ 'logistics.actions.carrier' | translate }}</label>
            <input id="ship-carrier" [value]="carrier()" (input)="carrier.set($any($event.target).value)" />
            <button appButton type="submit" [disabled]="busy()">{{ 'logistics.actions.create' | translate }}</button>
          </form>
        } @else if (canAct() && shipments().length === 0) {
          <p class="muted" role="status">{{ 'logistics.actions.notEligible' | translate }}</p>
        }

        @for (s of shipments(); track s.id) {
          <article class="shipment">
            <p class="muted small">
              <span class="label">{{ 'logistics.inbox.shipmentRef' | translate }}:</span>
              <bdi class="tabular">{{ s.number }}</bdi> · {{ 'logistics.kind.' + s.kind | translate }} ·
              {{ 'logistics.status.' + s.status | translate }}
            </p>
            <ul class="qtys">
              @for (line of s.lines; track line.id) {
                <li>
                  {{ line.description }} —
                  <span class="tabular">{{ line.quantity }} {{ line.unit }}</span>
                </li>
              }
            </ul>
            <ul class="milestones">
              @for (m of s.milestones; track m.id) {
                <li [class.late]="m.late">
                  <span>{{ 'logistics.milestone.' + m.code | translate }}</span>
                  @if (m.late) {
                    <span class="badge">{{ 'logistics.actions.late' | translate }}</span>
                  }
                  <span class="dates">
                    {{ m.plannedDate || '—' }} → {{ m.actualDate || '—' }}
                  </span>
                  @if (canAct() && !m.actualDate) {
                    <button appButton type="button" [disabled]="busy()" (click)="complete(s, m.id)">
                      {{ 'logistics.actions.recordActual' | translate }}
                    </button>
                  }
                </li>
              }
            </ul>
            <ul class="docs">
              @for (d of s.documents; track d.id) {
                <li>{{ 'logistics.doc.' + d.kind | translate }}: {{ d.referenceNumber || '—' }}</li>
              }
            </ul>
            @if (canAct()) {
              <form class="form" (submit)="$event.preventDefault(); addDoc(s.id)" novalidate>
                <label class="label" for="doc-ref-{{ s.id }}">{{ 'logistics.actions.docRef' | translate }}</label>
                <input id="doc-ref-{{ s.id }}" [value]="docRef()" (input)="docRef.set($any($event.target).value)" />
                <button appButton type="submit" [disabled]="busy() || !docRef().trim()">
                  {{ 'logistics.actions.addDoc' | translate }}
                </button>
              </form>
            }
          </article>
        }
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .form { display: grid; gap: var(--space-2); margin-block-end: var(--space-3); }
    .label { font-weight: var(--weight-medium); font-size: var(--text-sm); }
    select, input {
      min-block-size: 2.5rem;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
    }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
    .error { color: var(--color-danger); }
    .milestones, .docs { list-style: none; padding: 0; margin: 0 0 var(--space-3); display: grid; gap: var(--space-2); }
    .milestones li { display: grid; gap: var(--space-1); padding: var(--space-2); border-inline-start: 3px solid var(--color-line); }
    .milestones li.late { border-inline-start-color: var(--color-danger); }
    .badge { color: var(--color-danger); font-size: var(--text-sm); }
    .dates { font-size: var(--text-sm); color: var(--color-text-muted); }
    .refs { display: flex; flex-wrap: wrap; gap: var(--space-3); }
    .tabular { user-select: all; overflow-wrap: anywhere; font-weight: var(--weight-semibold); }
    .qtys { list-style: none; margin: 0 0 var(--space-2); padding: 0; font-size: var(--text-sm); }
  `,
})
export class LogisticsActions {
  readonly request = input.required<PurchaseRequest>();
  readonly changed = output<void>();

  private readonly api = inject(LogisticsApi);
  private readonly orderingApi = inject(OrderingApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly shipments = signal<ShipmentView[]>([]);
  protected readonly poConfirmed = signal(false);
  protected readonly poNumber = signal<string | null>(null);
  protected readonly kind = signal<'LOCAL' | 'INTERNATIONAL'>('LOCAL');
  protected readonly carrier = signal('Demo Freight');
  protected readonly docRef = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly visible = signal(false);

  constructor() {
    effect(() => {
      const status = this.request().status;
      const id = this.request().id;
      untracked(() => {
        const show =
          status === 'ORDERED' ||
          status === 'ORDER_CONFIRMED' ||
          status === 'IN_PRODUCTION' ||
          status === 'READY' ||
          status === 'SHIPPED' ||
          status === 'AT_PORT' ||
          status === 'IN_CUSTOMS' ||
          status === 'IN_WAREHOUSE' ||
          status === 'DELIVERED';
        this.visible.set(show && (this.canAct() || this.canViewAsAuthor()));
        if (show) {
          this.reload(id);
        } else {
          this.shipments.set([]);
          this.poConfirmed.set(false);
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

  protected canCreate(): boolean {
    const status = this.request().status;
    const shipping =
      status === 'ORDERED' ||
      status === 'ORDER_CONFIRMED' ||
      status === 'IN_PRODUCTION' ||
      status === 'READY' ||
      status === 'SHIPPED' ||
      status === 'AT_PORT' ||
      status === 'IN_CUSTOMS' ||
      status === 'IN_WAREHOUSE';
    return this.canAct() && this.poConfirmed() && shipping;
  }

  create(): void {
    this.busy.set(true);
    this.error.set(null);
    const yesterday = new Date();
    yesterday.setUTCDate(yesterday.getUTCDate() - 1);
    const planned = yesterday.toISOString().slice(0, 10);
    this.api
      .create(
        this.request().id,
        {
          requestVersion: this.request().version,
          kind: this.kind(),
          carrierName: this.carrier().trim() || undefined,
          milestonePlans: [{ code: 'DISPATCHED', plannedDate: planned }],
        },
        newIdempotencyKey(),
      )
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('logistics.actions.created');
          this.reload(this.request().id);
          this.changed.emit();
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  complete(shipment: ShipmentView, milestoneId: number): void {
    this.busy.set(true);
    this.error.set(null);
    const today = new Date().toISOString().slice(0, 10);
    this.api.updateMilestone(shipment.id, milestoneId, { version: shipment.version, actualDate: today }).subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.reloadSeq++; // This response is newer than any reload still in flight.
        this.shipments.set(this.shipments().map((s) => (s.id === updated.id ? updated : s)));
        this.notifications.success('logistics.actions.milestoneDone');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }

  addDoc(shipmentId: number): void {
    if (!this.docRef().trim()) {
      return;
    }
    const shipment = this.shipments().find((s) => s.id === shipmentId);
    this.busy.set(true);
    this.api
      .addDocument(shipmentId, {
        kind: shipment?.kind === 'INTERNATIONAL' ? 'BILL_OF_LADING' : 'DELIVERY_NOTE',
        referenceNumber: this.docRef().trim(),
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.docRef.set('');
          this.notifications.success('logistics.actions.docAdded');
          this.reload(this.request().id);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          this.error.set(toApiProblem(error).code);
        },
      });
  }

  /** Identifies the newest reload, so a slower, older response cannot overwrite it. */
  private reloadSeq = 0;

  private reload(requestId: number): void {
    const seq = ++this.reloadSeq;
    this.orderingApi.getPo(requestId).subscribe({
      next: (po) => {
        if (seq === this.reloadSeq) {
          this.poConfirmed.set(po.status === 'CONFIRMED');
          this.poNumber.set(po.number ?? null);
        }
      },
      error: () => {
        if (seq === this.reloadSeq) {
          this.poConfirmed.set(false);
          this.poNumber.set(null);
        }
      },
    });
    this.api.list(requestId).subscribe({
      next: (list) => {
        if (seq === this.reloadSeq) {
          this.shipments.set(list);
        }
      },
      error: () => {
        if (seq === this.reloadSeq) {
          this.shipments.set([]);
        }
      },
    });
  }
}
