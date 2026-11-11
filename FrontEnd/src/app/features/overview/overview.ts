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
import { StageRing, StageRingTone } from '../../shared/ui/stage-ring';
import { StatusBadge } from '../../shared/ui/status-badge';
import { NAVIGATION, NavItem } from '../../layout/navigation';
import { ApprovalCountService } from '../approvals/approval-count.service';
import { PurchasingCountService } from '../purchasing/purchasing-count.service';
import { ApprovalsApi } from '../approvals/approvals.api';
import { InboxItem } from '../approvals/approval.models';
import { PurchasingApi } from '../purchasing/purchasing.api';
import { QueueItem } from '../purchasing/purchasing.models';
import { Page, RequestListItem } from '../requests/request.models';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { ringToneOf, trackerIndex, userStatusOf, userStatusTone } from '../requests/request-status';
import { elapsedText, untilText } from '../../shared/relative-time';
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
    StageRing,
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
        <p class="supplier-message">{{ 'overview.supplier.message' | translate }}</p>
        <a appButton routerLink="/supplier/messages">{{ 'overview.supplier.open' | translate }}</a>
      </app-panel>
    } @else if (state() === 'loading') {
      <div class="loading">
        <app-skeleton height="7rem" />
        <app-skeleton height="4.5rem" />
        <app-skeleton height="14rem" />
      </div>
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
            <p class="next-label">
              <span class="next-pulse" aria-hidden="true"></span>
              {{ 'overview.next.count' | translate: { count: approvalTotal() } }}
            </p>
            <h2 class="next-title">{{ next.title }}</h2>
            <p class="next-meta">
              <bdi class="mono">{{ next.number }}</bdi>
              <span class="tabular next-amount"><bdi>{{ money(next) }}</bdi></span>
              <span>{{ 'approvals.inbox.waiting' | translate: { elapsed: elapsed(next.waitingSince) } }}</span>
              @if (next.dueAt) {
                <span class="next-due" [class.is-late]="isOverdue(next)">
                  @if (isOverdue(next)) {
                    <app-icon name="triangle-alert" [size]="14" />
                  }
                  {{ (isOverdue(next) ? 'tracker.overdue' : 'tracker.due') | translate: { when: until(next.dueAt) } }}
                </span>
              }
            </p>
          </div>
          <a appButton class="next-cta" routerLink="/approvals">
            {{ 'overview.next.action' | translate }}
            <app-icon name="arrow-right" [size]="16" />
          </a>
        </section>
      }

      @if (shortcuts().length > 1) {
        <nav class="shortcuts" [attr.aria-label]="'overview.shortcuts' | translate">
          @for (item of shortcuts(); track item.key) {
            <a class="shortcut" [routerLink]="item.route">
              <span class="shortcut-icon"><app-icon [name]="item.icon" [size]="18" /></span>
              <span class="shortcut-label">{{ 'nav.items.' + item.key | translate }}</span>
              @if (item.badge === 'approvals' && approvalCount.count() > 0) {
                <span class="shortcut-count tabular">
                  {{ approvalCount.count() }}
                  <span class="visually-hidden">{{ 'nav.pendingCount' | translate: { count: approvalCount.count() } }}</span>
                </span>
              } @else if (item.badge === 'purchasing' && purchasingCount.count() > 0) {
                <span class="shortcut-count tabular">
                  {{ purchasingCount.count() }}
                  <span class="visually-hidden">{{ 'nav.pendingCount' | translate: { count: purchasingCount.count() } }}</span>
                </span>
              } @else {
                <app-icon class="shortcut-go" name="chevron-right" [size]="16" />
              }
            </a>
          }
        </nav>
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
                    <app-status-badge [tone]="isOverdue(item) ? 'danger' : 'warning'">{{ 'overview.awaiting.approve' | translate }}</app-status-badge>
                    <span class="ref tabular">
                      <bdi>{{ item.number || ('requests.list.draftNumber' | translate) }}</bdi>
                      @if (item.dueAt) {
                        <span class="due" [class.is-late]="isOverdue(item)">{{ (isOverdue(item) ? 'tracker.overdue' : 'tracker.due') | translate: { when: until(item.dueAt) } }}</span>
                      }
                    </span>
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
                <a appButton variant="secondary" size="small" routerLink="/approvals">{{ 'overview.awaiting.openApprovals' | translate }}</a>
              }
              @if (canPurchase()) {
                <a appButton variant="secondary" size="small" routerLink="/purchasing">{{ 'overview.awaiting.openPurchasing' | translate }}</a>
              }
              @if (canPurchase()) {
                <a appButton variant="secondary" size="small" routerLink="/logistics">{{ 'overview.awaiting.openLogistics' | translate }}</a>
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
            <ul class="work-list work-list--ring">
              @for (item of mine(); track item.id) {
                <li>
                  <a [routerLink]="['/requests', item.id]">
                    <app-stage-ring class="ring" [step]="step(item)" [tone]="ringTone(item)" [size]="26" />
                    <span class="title">{{ item.title }}</span>
                    <app-status-badge [tone]="tone(item)">{{ 'requests.userStatus.' + (item.userStatus || item.status) | translate }}</app-status-badge>
                    <span class="ref tabular"><bdi>{{ item.number || ('requests.list.draftNumber' | translate) }}</bdi></span>
                  </a>
                </li>
              }
            </ul>
            <div class="links">
              <a appButton variant="secondary" size="small" routerLink="/requests">{{ 'overview.mine.openAll' | translate }}</a>
            </div>
          }
        </app-panel>

        <app-panel [heading]="'overview.notices.title' | translate">
          @if (notices().length === 0) {
            <p class="muted">{{ 'overview.notices.empty' | translate }}</p>
          } @else {
            <ul class="work-list work-list--notices">
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
    .loading {
      display: grid;
      gap: var(--space-4);
    }
    .supplier-message {
      margin-block-end: var(--space-4);
    }

    /* The one feature surface of the page: what is waiting for this person, on ink, with the saffron signal. */
    .next {
      position: relative;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-5);
      overflow: hidden;
      margin-block-end: var(--space-5);
      padding: var(--space-6) var(--space-8);
      border-radius: var(--radius-xl);
      background:
        radial-gradient(60% 140% at 100% 0%, var(--color-ink-glow), transparent 70%),
        var(--color-ink);
      border: 1px solid var(--color-ink-line);
      color: var(--color-on-ink-muted);
      box-shadow: var(--shadow-raised);
    }
    :host-context([dir='rtl']) .next {
      background:
        radial-gradient(60% 140% at 0% 0%, var(--color-ink-glow), transparent 70%),
        var(--color-ink);
    }
    .next :focus-visible {
      outline-color: var(--color-brand);
    }
    .next-body {
      display: grid;
      gap: var(--space-2);
      min-inline-size: 0;
    }
    .next-label {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      color: var(--color-brand);
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
    }
    .next-pulse {
      inline-size: 0.5rem;
      block-size: 0.5rem;
      border-radius: 50%;
      background: var(--color-brand);
      box-shadow: 0 0 0 4px var(--color-brand-glow);
    }
    .next-title {
      color: var(--color-on-ink);
      font-family: var(--font-display);
      font-size: 1.625rem;
      font-weight: 600;
      overflow-wrap: anywhere;
    }
    .next-meta {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-1) var(--space-5);
      font-size: var(--text-sm);
    }
    .next-amount {
      color: var(--color-on-ink);
      font-weight: var(--weight-semibold);
    }
    .next-due {
      display: inline-flex;
      align-items: center;
      gap: var(--space-1);
    }
    .next-due.is-late {
      color: var(--color-brand);
      font-weight: var(--weight-semibold);
    }
    .next .next-cta {
      min-block-size: 2.75rem;
      padding-inline: var(--space-5);
      background: var(--color-brand);
      color: var(--color-on-brand);
      box-shadow: none;
    }
    .next .next-cta:hover {
      background: var(--color-on-ink);
      color: var(--color-ink);
    }

    /* The direct path to each role's daily work: the same destinations as the navigation, one tap away. */
    .shortcuts {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 13rem), 1fr));
      gap: var(--space-3);
      margin-block-end: var(--space-6);
    }
    .shortcut {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      min-block-size: 3.5rem;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
      color: var(--color-text);
      font-weight: var(--weight-semibold);
      text-decoration: none;
      box-shadow: var(--shadow-card);
      transition:
        border-color var(--duration-fast) var(--ease-standard),
        box-shadow var(--duration-fast) var(--ease-standard);
    }
    .shortcut:hover {
      border-color: var(--color-accent);
      color: var(--color-text);
      box-shadow: var(--shadow-raised);
    }
    .shortcut-icon {
      display: grid;
      place-items: center;
      flex-shrink: 0;
      inline-size: 2.25rem;
      block-size: 2.25rem;
      border-radius: var(--radius-md);
      background: var(--color-accent-soft);
      color: var(--color-accent);
    }
    .shortcut-label {
      flex: 1;
      min-inline-size: 0;
      overflow-wrap: anywhere;
    }
    .shortcut-count {
      flex-shrink: 0;
      min-inline-size: 1.5rem;
      padding-inline: var(--space-2);
      border-radius: var(--radius-pill);
      background: var(--color-brand);
      color: var(--color-on-brand);
      font-size: var(--text-xs);
      line-height: 1.5rem;
      text-align: center;
    }
    .shortcut-go {
      flex-shrink: 0;
      color: var(--color-text-muted);
    }

    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 22rem), 1fr));
      gap: var(--space-5);
      align-items: start;
    }
    .work-list {
      list-style: none;
      margin: calc(-1 * var(--space-2)) 0 var(--space-3);
      padding: 0;
    }
    .work-list li + li {
      border-block-start: 1px solid var(--color-line);
    }
    .work-list a {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 0 var(--space-3);
      margin-inline: calc(-1 * var(--space-3));
      padding: var(--space-3);
      border-radius: var(--radius-sm);
      text-decoration: none;
      color: inherit;
      min-block-size: 2.75rem;
      transition: background-color var(--duration-fast) var(--ease-standard);
    }
    .work-list a:hover {
      background: var(--color-surface-sunken);
      color: inherit;
    }
    .work-list a:focus-visible {
      outline: 2px solid var(--color-focus);
      outline-offset: -2px;
    }
    .work-list--ring a {
      grid-template-columns: auto minmax(0, 1fr) auto;
    }
    .ring {
      grid-row: span 2;
    }
    .ref {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
      overflow-wrap: anywhere;
      grid-column: 1 / -1;
    }
    .work-list--ring .ref {
      grid-column: 2 / -1;
    }
    .ref {
      display: flex;
      flex-wrap: wrap;
      gap: 0 var(--space-3);
    }
    .due.is-late {
      color: var(--color-danger);
      font-weight: var(--weight-semibold);
    }
    .title {
      font-weight: var(--weight-medium);
      overflow-wrap: anywhere;
    }
    .work-list--notices .title {
      font-weight: var(--weight-regular);
    }
    .when {
      font-size: var(--text-xs);
      color: var(--color-text-muted);
      white-space: nowrap;
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
    @media (max-width: 40rem) {
      .next {
        padding: var(--space-5);
        border-radius: var(--radius-lg);
      }
      .next-title {
        font-size: 1.25rem;
      }
      .next .next-cta {
        inline-size: 100%;
      }
      .shortcuts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .shortcut {
        flex-wrap: wrap;
      }
      .shortcut-go {
        display: none;
      }
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
  protected readonly approvalCount = inject(ApprovalCountService);
  protected readonly purchasingCount = inject(PurchasingCountService);

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

  /**
   * The destinations this person's role uses every day, taken from the navigation (same permissions, same routes),
   * one entry per route.
   */
  protected readonly shortcuts = computed<NavItem[]>(() => {
    const me = this.auth.me();
    const seen = new Set<string>(['/overview', '/system-status', '/account/password']);
    return NAVIGATION.flatMap((group) => group.items)
      .filter((item) => !!item.route && (!item.permission || hasPermission(me, item.permission)))
      .filter((item) => (seen.has(item.route!) ? false : !!seen.add(item.route!)))
      .slice(0, 6);
  });

  constructor() {
    this.load();
  }

  protected step(item: RequestListItem): number {
    return trackerIndex(userStatusOf(item));
  }

  protected ringTone(item: RequestListItem): StageRingTone {
    return ringToneOf(userStatusOf(item));
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

  protected until(iso: string): string {
    return untilText(iso, Date.now(), this.language.locale());
  }

  /** Same rule as the approvals inbox: the server's flag, or a due time that has passed. */
  protected isOverdue(item: InboxItem): boolean {
    return item.status === 'PENDING' && (!!item.overdue || (!!item.dueAt && Date.parse(item.dueAt) < Date.now()));
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
