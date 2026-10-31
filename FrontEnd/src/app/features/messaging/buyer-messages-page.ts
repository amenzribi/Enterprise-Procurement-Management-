import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { MessagingApi } from './messaging.api';
import { ConversationSummary } from './messaging.models';

@Component({
  selector: 'app-buyer-messages-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, EmptyState, ErrorState, PageHeader, RouterLink, Skeleton, TranslatePipe],
  template: `
    <app-page-header [heading]="'messaging.buyer.title' | translate" [description]="'messaging.buyer.subtitle' | translate" />
    @if (loading()) {
      <app-skeleton />
    } @else if (error(); as code) {
      <app-error-state [heading]="'errors.' + code | translate" />
    } @else if (!conversations().length) {
      <app-empty-state [heading]="'messaging.empty' | translate" [message]="'messaging.buyer.emptyHint' | translate" />
    } @else {
      <ul class="list">
        @for (c of conversations(); track c.id) {
          <li>
            <a [routerLink]="['/requests', c.requestId]">
              <strong>{{ c.supplierName }}</strong>
              <span class="muted">{{ c.contextType }} #{{ c.contextId }}</span>
              @if (c.unreadCount > 0) {
                <span class="badge">{{ 'messaging.unread' | translate: { count: c.unreadCount } }}</span>
              }
              @if (c.lastMessageAt; as at) {
                <time class="muted small" [attr.datetime]="at">{{ at | date: 'short' }}</time>
              }
            </a>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: var(--space-2); }
    .list a { display: grid; gap: 0.25rem; padding: var(--space-3); border: 1px solid var(--color-border); text-decoration: none; color: inherit; }
    .badge { color: var(--color-accent, #0f766e); font-weight: 600; }
    .muted { color: var(--color-text-muted); }
    .small { font-size: var(--text-sm); }
  `,
})
export class BuyerMessagesPage implements OnInit {
  private readonly api = inject(MessagingApi);
  protected readonly conversations = signal<ConversationSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.listBuyer().subscribe({
      next: (list) => {
        this.conversations.set(list);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(toApiProblem(err).code);
        this.loading.set(false);
      },
    });
  }
}
