import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { PurchaseRequest } from '../requests/request.models';
import { PurchasingApi } from './purchasing.api';

/** After approval and before award. */
const DECISION_STAGES = ['IN_PURCHASING_REVIEW', 'ASSIGNED_TO_BUYER', 'SOURCING', 'QUOTES_RECEIVED', 'TECHNICAL_VALIDATION', 'COMMERCIAL_VALIDATION', 'MANAGEMENT_VALIDATION'];

/** Purchasing confirms each line the requester declared a service, or sends it through shipment and receipt as goods. */
@Component({
  selector: 'app-line-classification',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    @if (open().length > 0) {
      <section class="panel decide" aria-labelledby="classification-title">
        <h2 class="panel-title" id="classification-title">{{ 'purchasing.classification.title' | translate }}</h2>
        <p class="muted small">{{ 'purchasing.classification.hint' | translate }}</p>
        @if (error()) {
          <p class="error" role="alert">{{ 'errors.' + error() | translate }}</p>
        }
        <ul class="lines">
          @for (line of open(); track line.id) {
            <li>
              <span><bdi>{{ line.description }}</bdi></span>
              <label class="check">
                <input type="checkbox" [id]="'classify-goods-' + line.id" [checked]="asGoods().has(line.id!)" (change)="toggle(line.id!, $any($event.target).checked)" />
                {{ 'purchasing.classification.asGoods' | translate }}
              </label>
            </li>
          }
        </ul>
        <button appButton type="button" [disabled]="busy()" (click)="decide()">{{ 'purchasing.classification.confirm' | translate }}</button>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
    .error { color: var(--color-danger); }
    .lines { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: var(--space-2); }
    .lines li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: var(--space-2); }
    .check { display: inline-flex; gap: var(--space-2); align-items: center; font-size: var(--text-sm); }
  `,
})
export class LineClassification {
  readonly request = input.required<PurchaseRequest>();
  readonly changed = output<void>();

  private readonly api = inject(PurchasingApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly asGoods = signal<ReadonlySet<number>>(new Set());
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly open = computed(() => {
    const request = this.request();
    const me = this.auth.me();
    const purchasing = hasPermission(me, 'PURCHASING_ACT') || hasPermission(me, 'PURCHASING_MANAGE');
    const author = !!me && (me.id === request.requester?.id || me.id === request.enteredBy?.id);
    if (!purchasing || author || !DECISION_STAGES.includes(request.status)) {
      return [];
    }
    return (request.lines ?? []).filter((line) => line.lineKind === 'SERVICE' && !line.serviceConfirmed);
  });

  protected toggle(lineId: number, checked: boolean): void {
    this.asGoods.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(lineId);
      } else {
        next.delete(lineId);
      }
      return next;
    });
  }

  protected decide(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.decideLineClassification(this.request().id, { version: this.request().version, reclassifyAsGoods: [...this.asGoods()] }).subscribe({
      next: () => {
        this.busy.set(false);
        this.asGoods.set(new Set());
        this.notifications.success('purchasing.classification.done');
        this.changed.emit();
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.error.set(toApiProblem(error).code);
      },
    });
  }
}
