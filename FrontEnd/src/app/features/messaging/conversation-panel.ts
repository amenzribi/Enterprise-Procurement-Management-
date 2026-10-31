import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { MessagingApi } from './messaging.api';
import { ConversationDetail, MessageAudience, MessageView } from './messaging.models';

/**
 * Buyer-side RFQ/PO conversation panel (Sprint 11). INTERNAL vs EXTERNAL is explicit.
 */
@Component({
  selector: 'app-conversation-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, TranslatePipe],
  template: `
    @if (visible()) {
      <section class="panel messaging" aria-labelledby="msg-title">
        <h2 class="panel-title" id="msg-title">{{ 'messaging.panel.title' | translate }}</h2>
        <p class="muted small">{{ 'messaging.panel.hint' | translate }}</p>

        @if (!conversation()) {
          <button appButton type="button" [disabled]="busy()" (click)="openThread()">
            {{ 'messaging.panel.open' | translate }}
          </button>
        } @else {
          <p class="muted small">
            {{ conversation()!.supplierName }} ·
            {{ 'messaging.status.' + conversation()!.status | translate }}
            @if (conversation()!.unreadCount > 0) {
              · {{ 'messaging.unread' | translate: { count: conversation()!.unreadCount } }}
            }
          </p>

          <div class="messages" role="log" aria-live="polite">
            @for (m of messages(); track m.id) {
              <article class="bubble" [class.internal]="m.audience === 'INTERNAL'" [class.external]="m.audience === 'EXTERNAL'">
                <header>
                  <span class="audience">{{ 'messaging.audience.' + m.audience | translate }}</span>
                  · {{ m.senderDisplayName }} ·
                  <time [attr.datetime]="m.createdAt">{{ m.createdAt | date: 'short' }}</time>
                </header>
                <p class="body">{{ m.bodyText }}</p>
              </article>
            } @empty {
              <p class="muted">{{ 'messaging.empty' | translate }}</p>
            }
          </div>

          @if (conversation()!.writable) {
            <form class="form" (submit)="$event.preventDefault(); send()" novalidate>
              <fieldset>
                <legend>{{ 'messaging.audienceLabel' | translate }}</legend>
                <label class="radio">
                  <input type="radio" name="aud" value="EXTERNAL" [checked]="audience() === 'EXTERNAL'" (change)="audience.set('EXTERNAL')" />
                  {{ 'messaging.audience.EXTERNAL' | translate }}
                </label>
                <label class="radio">
                  <input type="radio" name="aud" value="INTERNAL" [checked]="audience() === 'INTERNAL'" (change)="audience.set('INTERNAL')" />
                  {{ 'messaging.audience.INTERNAL' | translate }}
                </label>
              </fieldset>
              @if (audience() === 'INTERNAL') {
                <p class="warn small" role="status">{{ 'messaging.internalWarn' | translate }}</p>
              }
              <label class="label" for="msg-body">{{ 'messaging.body' | translate }} *</label>
              <textarea id="msg-body" rows="3" maxlength="4000" [value]="body()" (input)="body.set($any($event.target).value)"></textarea>
              <p class="error small" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>
              <button appButton type="submit" [disabled]="busy() || !body().trim()">{{ 'messaging.send' | translate }}</button>
            </form>
          } @else {
            <p class="muted">{{ 'messaging.readOnly' | translate }}</p>
          }
        }
      </section>
    }
  `,
  styles: `
    .messaging { margin-block-start: var(--space-4); }
    .messages { display: flex; flex-direction: column; gap: var(--space-2); max-block-size: 20rem; overflow: auto; margin-block: var(--space-3); }
    .bubble { padding: var(--space-2); border-inline-start: 3px solid var(--color-border); }
    .bubble.internal { border-inline-start-color: var(--color-warning, #b45309); background: color-mix(in srgb, var(--color-warning, #b45309) 8%, transparent); }
    .bubble.external { border-inline-start-color: var(--color-accent, #0f766e); }
    .audience { font-weight: 600; text-transform: uppercase; font-size: 0.75rem; }
    .body { white-space: pre-wrap; margin: 0.25rem 0 0; }
    .warn { color: var(--color-warning, #b45309); }
    .radio { display: inline-flex; align-items: center; gap: 0.35rem; margin-inline-end: 1rem; }
    textarea { width: 100%; }
  `,
})
export class ConversationPanel {
  readonly contextType = input.required<'RFQ' | 'PO'>();
  readonly contextId = input.required<number>();
  readonly supplierId = input.required<number>();

  private readonly api = inject(MessagingApi);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);

  protected readonly conversation = signal<ConversationDetail | null>(null);
  protected readonly messages = signal<MessageView[]>([]);
  protected readonly audience = signal<MessageAudience>('EXTERNAL');
  protected readonly body = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly visible = () => hasPermission(this.auth.me(), 'MESSAGING_BUYER') && this.contextId() > 0 && this.supplierId() > 0;

  constructor() {
    effect(() => {
      if (!this.visible()) {
        return;
      }
      const type = this.contextType();
      const id = this.contextId();
      const supplier = this.supplierId();
      if (type && id && supplier) {
        this.tryLoadExisting();
      }
    });
  }

  protected openThread(): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.open(this.contextType(), this.contextId(), this.supplierId(), newIdempotencyKey()).subscribe({
      next: (c) => {
        this.conversation.set(c);
        this.loadMessages(c.id);
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(toApiProblem(err).code);
        this.busy.set(false);
      },
    });
  }

  protected send(): void {
    const c = this.conversation();
    if (!c || !this.body().trim()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const clientId = newIdempotencyKey();
    this.api.sendBuyer(c.id, this.audience(), this.body().trim(), clientId, clientId).subscribe({
      next: (m) => {
        this.messages.update((list) => [...list, m]);
        this.body.set('');
        this.busy.set(false);
        this.notify.success('messaging.sent');
      },
      error: (err) => {
        this.error.set(toApiProblem(err).code);
        this.busy.set(false);
      },
    });
  }

  private tryLoadExisting(): void {
    this.api.listBuyer(this.supplierId()).subscribe({
      next: (list) => {
        const match = list.find(
          (c) => c.contextType === this.contextType() && c.contextId === this.contextId() && c.supplierId === this.supplierId(),
        );
        if (match) {
          this.api.getBuyer(match.id).subscribe({
            next: (detail) => {
              this.conversation.set(detail);
              this.loadMessages(detail.id);
            },
          });
        }
      },
    });
  }

  private loadMessages(id: number): void {
    this.api.messagesBuyer(id).subscribe({
      next: (page) => {
        this.messages.set(page.items);
        const last = page.items.at(-1);
        if (last) {
          this.api.markReadBuyer(id, last.id).subscribe({ error: () => undefined });
        }
      },
    });
  }
}
