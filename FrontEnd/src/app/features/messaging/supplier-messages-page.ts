import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { MessagingApi } from './messaging.api';
import { ConversationDetail, ConversationSummary, MessageView } from './messaging.models';

@Component({
  selector: 'app-supplier-messages-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, PageHeader, RouterLink, Skeleton, TranslatePipe],
  template: `
    <app-page-header [heading]="'messaging.portal.title' | translate" [description]="'messaging.portal.subtitle' | translate" />

    @if (loading()) {
      <app-skeleton />
    } @else if (error(); as code) {
      <app-error-state [heading]="'errors.' + code | translate" />
    } @else {
      <div class="layout">
        <aside class="list" aria-label="{{ 'messaging.portal.list' | translate }}">
          @for (c of conversations(); track c.id) {
            <a class="item" [routerLink]="['/supplier/messages', c.id]" [class.active]="selectedId() === c.id">
              <strong>{{ c.supplierName }}</strong>
              <span class="muted small">{{ c.contextType }} #{{ c.contextId }}</span>
              @if (c.unreadCount > 0) {
                <span class="badge">{{ c.unreadCount }}</span>
              }
            </a>
          } @empty {
            <app-empty-state [heading]="'messaging.empty' | translate" />
          }
        </aside>

        <section class="thread">
          @if (detail(); as d) {
            <h2>{{ d.contextType }} #{{ d.contextId }}</h2>
            <div class="messages" role="log">
              @for (m of messages(); track m.id) {
                <article class="bubble">
                  <header>{{ m.senderDisplayName }} · <time [attr.datetime]="m.createdAt">{{ m.createdAt | date: 'short' }}</time></header>
                  <p class="body">{{ m.bodyText }}</p>
                </article>
              }
            </div>
            @if (d.writable) {
              <form class="form" (submit)="$event.preventDefault(); send()" novalidate>
                <label class="label" for="portal-body">{{ 'messaging.body' | translate }} *</label>
                <textarea id="portal-body" rows="3" maxlength="4000" [value]="body()" (input)="body.set($any($event.target).value)"></textarea>
                <button appButton type="submit" [disabled]="busy() || !body().trim()">{{ 'messaging.send' | translate }}</button>
              </form>
            } @else {
              <p class="muted">{{ 'messaging.readOnly' | translate }}</p>
            }
          } @else if (conversations().length) {
            <p class="muted">{{ 'messaging.portal.pick' | translate }}</p>
          }
        </section>
      </div>
    }
  `,
  styles: `
    .layout { display: grid; grid-template-columns: minmax(12rem, 18rem) 1fr; gap: var(--space-4); }
    @media (max-width: 40rem) { .layout { grid-template-columns: 1fr; } }
    .list { display: flex; flex-direction: column; gap: var(--space-2); }
    .item { display: flex; flex-direction: column; padding: var(--space-2); border: 1px solid var(--color-border); text-decoration: none; color: inherit; position: relative; }
    .item.active { outline: 2px solid var(--color-accent, #0f766e); }
    .badge { position: absolute; inset-inline-end: 0.5rem; inset-block-start: 0.5rem; background: var(--color-accent, #0f766e); color: #fff; border-radius: 999px; padding: 0 0.4rem; font-size: 0.75rem; }
    .messages { display: flex; flex-direction: column; gap: var(--space-2); max-block-size: 24rem; overflow: auto; margin-block: var(--space-3); }
    .bubble { padding: var(--space-2); border-inline-start: 3px solid var(--color-accent, #0f766e); }
    .body { white-space: pre-wrap; margin: 0.25rem 0 0; }
    textarea { width: 100%; }
  `,
})
export class SupplierMessagesPage implements OnInit {
  private readonly api = inject(MessagingApi);
  private readonly route = inject(ActivatedRoute);
  private readonly notify = inject(NotificationService);

  protected readonly conversations = signal<ConversationSummary[]>([]);
  protected readonly detail = signal<ConversationDetail | null>(null);
  protected readonly messages = signal<MessageView[]>([]);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly body = signal('');
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.listPortal().subscribe({
      next: (list) => {
        this.conversations.set(list);
        this.loading.set(false);
        const idParam = this.route.snapshot.paramMap.get('id');
        if (idParam) {
          this.select(+idParam);
        }
      },
      error: (err) => {
        this.error.set(toApiProblem(err).code);
        this.loading.set(false);
      },
    });
    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id) {
        this.select(+id);
      }
    });
  }

  protected send(): void {
    const id = this.selectedId();
    if (id == null || !this.body().trim()) {
      return;
    }
    this.busy.set(true);
    const clientId = newIdempotencyKey();
    this.api.sendPortal(id, this.body().trim(), clientId, clientId).subscribe({
      next: (m) => {
        this.messages.update((list) => [...list, m]);
        this.body.set('');
        this.busy.set(false);
        this.notify.success('messaging.sent');
      },
      error: (err) => {
        this.notify.error(`errors.${toApiProblem(err).code}`);
        this.busy.set(false);
      },
    });
  }

  private select(id: number): void {
    this.selectedId.set(id);
    this.api.getPortal(id).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.api.messagesPortal(id).subscribe({
          next: (page) => {
            this.messages.set(page.items);
            const last = page.items.at(-1);
            if (last) {
              this.api.markReadPortal(id, last.id).subscribe({ error: () => undefined });
            }
          },
        });
      },
      error: (err) => this.notify.error(`errors.${toApiProblem(err).code}`),
    });
  }
}
