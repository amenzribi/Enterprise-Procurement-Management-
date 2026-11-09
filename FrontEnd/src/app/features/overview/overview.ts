import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Panel } from '../../shared/ui/panel';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { ApprovalsApi } from '../approvals/approvals.api';
import { InboxItem } from '../approvals/approval.models';
import { PurchasingApi } from '../purchasing/purchasing.api';
import { QueueItem } from '../purchasing/purchasing.models';
import { Page, RequestListItem } from '../requests/request.models';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { userStatusTone } from '../requests/request-status';
import { elapsedText } from '../../shared/relative-time';
import { RequestsApi } from '../requests/requests.api';

interface NoticeItem {
  id: number;
  title?: string;
  body?: string;
  linkPath?: string | null;
  createdAt?: string;
  readAt?: string | null;
}

type LoadState = 'loading' | 'ready' | 'error';

/**
 * Role-appropriate landing: items awaiting action, my requests, assignments and unread notices.
 * Data comes from existing APIs only — no invented actors or permissions.
 */
@Component({
  selector: 'app-overview',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
    DatePipe,
    EmptyState,
    ErrorState,
    Icon,
    PageHeader,
    Panel,
    RouterLink,
    Skeleton,
    StatusBadge,
    TranslatePipe,
  ],
  template: `
    <app-page-header [heading]="'overview.title' | translate" [description]="'overview.description' | translate">
      @if (canCreate()) {
        <a appButton routerLink="/requests/new">
          <app-icon name="file-plus" [size]="16" />
          {{ 'overview.newRequest' | translate }}
        </a>
      }
    </app-page-header>

    @if (isSupplier()) {
      <app-panel [heading]="'overview.supplier.title' | translate">
        <p>{{ 'overview.supplier.message' | translate }}</p>
        <a appButton routerLink="/supplier/messages">{{ 'overview.supplier.open' | translate }}</a>
      </app-panel>
    } @else if (state() === 'loading') {
      <app-skeleton height="12rem" />
    } @else if (state() === 'error') {
      <app-error-state
        [heading]="'overview.loadError' | translate"
        [retryLabel]="'common.retry' | translate"
        (retry)="load()"
      />
    } @else {
      @if (nextApproval(); as next) {
        <section class="next" [attr.aria-label]="'overview.next.label' | translate">
          <div class="next-body">
            <p class="next-label">{{ 'overview.next.count' | translate: { count: approvalTotal() } }}</p>
            <h2 class="next-title">{{ next.title }}</h2>
            <p class="next-meta">
              <bdi class="mono">{{ next.number }}</bdi>
              <span class="tabular"><bdi>{{ money(next) }}</bdi></span>
              <span>{{ 'approvals.inbox.waiting' | translate: { elapsed: elapsed(next.waitingSince) } }}</span>
            </p>
          </div>
          <a appButton routerLink="/approvals">{{ 'overview.next.action' | translate }}</a>
        </section>
      }
      <div class="grid">
        <app-panel [heading]="'overview.awaiting.title' | translate">
          @if (approvals().length === 0 && purchasing().length === 0) {
            <app-empty-state
              icon="circle-check"
              [heading]="'overview.awaiting.emptyTitle' | translate"
              [message]="'overview.awaiting.emptyMessage' | translate"
            />
          } @else {
            <ul class="work-list">
              @for (item of approvals(); track item.taskId) {
                <li>
                  <a [routerLink]="['/requests', item.requestId]">
                    <span class="title">{{ item.title }}</span>
                    <app-status-badge tone="warning">{{ 'overview.awaiting.approve' | translate }}</app-status-badge>
                    <span class="ref tabular"><bdi>{{ item.number || ('requests.list.draftNumber' | translate) }}</bdi></span>
                  </a>
                </li>
              }
              @for (item of purchasing(); track item.requestId) {
                <li>
                  <a [routerLink]="['/requests', item.requestId]">
                    <span class="title">{{ item.title }}</span>
                    <app-status-badge tone="info">{{ 'overview.awaiting.purchasing' | translate }}</app-status-badge>
                    <span class="ref tabular"><bdi>{{ item.number || ('requests.list.draftNumber' | translate) }}</bdi></span>
                  </a>
                </li>
              }
            </ul>
            <div class="links">
              @if (canApprove()) {
                <a appButton variant="secondary" routerLink="/approvals">{{ 'overview.awaiting.openApprovals' | translate }}</a>
              }
              @if (canPurchase()) {
                <a appButton variant="secondary" routerLink="/purchasing">{{ 'overview.awaiting.openPurchasing' | translate }}</a>
              }
              @if (canPurchase()) {
                <a appButton variant="secondary" routerLink="/logistics">{{ 'overview.awaiting.openLogistics' | translate }}</a>
              }
            </div>
          }
        </app-panel>

        <app-panel [heading]="'overview.mine.title' | translate">
          @if (mine().length === 0) {
            <app-empty-state
              icon="file-plus"
              [heading]="'overview.mine.emptyTitle' | translate"
              [message]="'overview.mine.emptyMessage' | translate"
            />
          } @else {
            <ul class="work-list">
              @for (item of mine(); track item.id) {
                <li>
                  <a [routerLink]="['/requests', item.id]">
                    <span class="title">{{ item.title }}</span>
                    <app-status-badge [tone]="tone(item)">{{ 'requests.userStatus.' + (item.userStatus || item.status) | translate }}</app-status-badge>
                    <span class="ref tabular"><bdi>{{ item.number || ('requests.list.draftNumber' | translate) }}</bdi></span>
                  </a>
                </li>
              }
            </ul>
            <a appButton variant="secondary" routerLink="/requests">{{ 'overview.mine.openAll' | translate }}</a>
          }
        </app-panel>

        <app-panel [heading]="'overview.notices.title' | translate">
          @if (notices().length === 0) {
            <p class="muted">{{ 'overview.notices.empty' | translate }}</p>
          } @else {
            <ul class="work-list">
              @for (n of notices(); track n.id) {
                <li>
                  <a [routerLink]="n.linkPath || '/overview'">
                    <span class="title">{{ n.title || n.body }}</span>
                    @if (n.createdAt) {
                      <time class="when" [attr.datetime]="n.createdAt">{{ n.createdAt | date: 'short' : undefined : locale() }}</time>
                    }
                  </a>
                </li>
              }
            </ul>
          }
        </app-panel>
      </div>
      <p class="demo-note">
        <app-icon name="triangle-alert" [size]="16" />
        <span>{{ 'overview.demoNote' | translate }}</span>
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .next {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-4);
      margin-block-end: var(--space-6);
      padding: var(--space-5);
      border: 1px solid var(--color-accent);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
    }
    .next-body {
      display: grid;
      gap: var(--space-1);
      min-inline-size: 0;
    }
    .next-label {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
    .next-title {
      font-size: var(--text-lg);
      overflow-wrap: anywhere;
    }
    .next-meta {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-1) var(--space-4);
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 22rem), 1fr));
      gap: var(--space-4);
      align-items: start;
    }
    .work-list {
      list-style: none;
      margin: 0 0 var(--space-3);
      padding: 0;
    }
    .work-list li + li {
      border-block-start: 1px solid var(--color-line);
    }
    .work-list a {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 0 var(--space-3);
      padding-block: var(--space-2);
      text-decoration: none;
      color: inherit;
      min-block-size: 2.75rem;
    }
    .work-list a:hover .title {
      text-decoration: underline;
    }
    .work-list a:focus-visible {
      outline: 2px solid var(--color-focus, var(--color-accent));
      outline-offset: 2px;
    }
    .ref {
      color: var(--color-text-muted);
      font-family: var(--font-mono);
      font-size: var(--text-xs);
      overflow-wrap: anywhere;
      grid-column: 1 / -1;
    }
    .title {
      overflow-wrap: anywhere;
    }
    .when {
      font-size: var(--text-sm);
      color: var(--color-text-muted);
    }
    .links {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }
    .muted {
      color: var(--color-text-muted);
    }
    .demo-note {
      display: flex;
      gap: var(--space-2);
      align-items: start;
      margin-block-start: var(--space-6);
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
  `,
})
export class OverviewPage {
  private readonly auth = inject(AuthService);
  private readonly requestsApi = inject(RequestsApi);
  private readonly approvalsApi = inject(ApprovalsApi);
  private readonly purchasingApi = inject(PurchasingApi);
  private readonly http = inject(HttpClient);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<LoadState>('loading');
  protected readonly approvals = signal<InboxItem[]>([]);
  protected readonly purchasing = signal<QueueItem[]>([]);
  protected readonly mine = signal<RequestListItem[]>([]);
  protected readonly notices = signal<NoticeItem[]>([]);

  protected readonly approvalTotal = signal(0);
  protected readonly nextApproval = computed(() => this.approvals()[0] ?? null);
  protected readonly isSupplier = computed(() => hasPermission(this.auth.me(), 'MESSAGING_SUPPLIER'));
  protected readonly canCreate = computed(() => hasPermission(this.auth.me(), 'REQUEST_CREATE'));
  protected readonly canApprove = computed(() => hasPermission(this.auth.me(), 'APPROVAL_ACT'));
  protected readonly canPurchase = computed(() => hasPermission(this.auth.me(), 'PURCHASING_ACT'));

  constructor() {
    this.load();
  }

  protected locale(): string {
    return this.language.locale();
  }

  protected money(item: InboxItem): string {
    return formatMoney(item.amount ?? null, item.currency, minorUnitsOf(item.currency), this.language.locale()) || '—';
  }

  protected elapsed(iso: string): string {
    return elapsedText(iso, Date.now(), this.language.locale());
  }

  protected tone(item: RequestListItem) {
    return userStatusTone((item.userStatus || item.status) as never);
  }

  protected load(): void {
    if (this.isSupplier()) {
      this.state.set('ready');
      return;
    }
    this.state.set('loading');
    const approvals$ = this.canApprove()
      ? this.approvalsApi
          .inbox({ view: 'pending', q: null, sort: 'waitingSince', direction: 'desc', page: 0, size: 8 })
          .pipe(catchError(() => of({ items: [] as InboxItem[], totalItems: 0 })))
      : of({ items: [] as InboxItem[], totalItems: 0 });
    const purchasing$ = this.canPurchase()
      ? this.purchasingApi
          .queue({
            q: null,
            status: null,
            priority: null,
            purchaseMode: null,
            buyingCompanyId: null,
            categoryId: null,
            countryCode: null,
            buyerUserId: null,
            requesterUserId: null,
            requiredFrom: null,
            requiredTo: null,
            amountMin: null,
            amountMax: null,
            complete: null,
            sort: 'submittedAt',
            direction: 'desc',
            page: 0,
            size: 8,
          })
          .pipe(catchError(() => of({ items: [] as QueueItem[] })))
      : of({ items: [] as QueueItem[] });
    const mine$ = this.requestsApi
      .list({
        scope: 'mine',
        status: null,
        q: null,
        sort: 'createdAt',
        direction: 'desc',
        page: 0,
        size: 8,
      })
      .pipe(catchError(() => of({ items: [] as RequestListItem[] } as Page<RequestListItem>)));
    const notices$ = this.http
      .get<{ items: NoticeItem[] }>('/api/v1/notifications', { params: { unread: 'true', size: '8' } })
      .pipe(catchError(() => of({ items: [] as NoticeItem[] })));

    forkJoin({ approvals: approvals$, purchasing: purchasing$, mine: mine$, notices: notices$ })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.approvals.set(result.approvals.items ?? []);
          this.approvalTotal.set(result.approvals.totalItems ?? 0);
          this.purchasing.set(result.purchasing.items ?? []);
          this.mine.set(result.mine.items ?? []);
          this.notices.set(result.notices.items ?? []);
          this.state.set('ready');
        },
        error: () => this.state.set('error'),
      });
  }
}
