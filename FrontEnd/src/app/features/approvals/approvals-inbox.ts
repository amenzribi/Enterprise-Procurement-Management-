import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { elapsedText, untilText } from '../../shared/relative-time';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { Page } from '../requests/request.models';
import { ApprovalCountService } from './approval-count.service';
import { ApprovalActions } from './approval-actions';
import { ApprovalView, INBOX_SORTS, InboxItem, InboxQuery, InboxSort, InboxView } from './approval.models';
import { ApprovalsApi } from './approvals.api';

type PageState = 'loading' | 'ready' | 'error';
type LoadResult = { kind: 'ready'; page: Page<InboxItem> } | { kind: 'error'; problem: ReturnType<typeof toApiProblem> };

const PAGE_SIZE = 20;
const MAX_SEARCH_LENGTH = 100;
const DECISION_EXIT_MS = 180;

/** Reads the inbox query from the URL so filters survive reloads and can be shared. Exported for tests. */
export function inboxQueryFromParams(params: ParamMap): InboxQuery {
  const sort = params.get('sort');
  const page = Number(params.get('page'));
  return {
    view: params.get('view') === 'decided' ? 'decided' : 'pending',
    q: params.get('q')?.trim().slice(0, MAX_SEARCH_LENGTH) || null,
    sort: (INBOX_SORTS as readonly string[]).includes(sort ?? '') ? (sort as InboxSort) : 'waitingSince',
    direction: params.get('dir') === 'asc' ? 'asc' : 'desc',
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZE,
  };
}

/**
 * "To approve": the decisions waiting for the current user, oldest due first, readable on a phone (S2-AC-08). Each
 * card opens the request, where the decision panel sits above the details.
 */
@Component({
  selector: 'app-approvals-inbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ApprovalActions, Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'approvals.inbox.title' | translate" [description]="'approvals.inbox.description' | translate">
      <a appButton variant="secondary" routerLink="/absences">
        <app-icon name="calendar" [size]="16" />
        {{ 'absences.menu' | translate }}
      </a>
    </app-page-header>

    <div class="toolbar" role="search" [attr.aria-label]="'approvals.inbox.filtersLabel' | translate">
      <div class="tabs" role="group" [attr.aria-label]="'approvals.inbox.viewLabel' | translate">
        @for (view of views; track view) {
          <button type="button" class="tab" [attr.aria-pressed]="query().view === view" (click)="navigate({ view, page: null })">
            {{ 'approvals.inbox.views.' + view | translate }}
            @if (view === 'pending' && count.count() > 0) {
              <span class="tab-count tabular">{{ count.count() }}</span>
            }
          </button>
        }
      </div>
      <div class="field">
        <label for="inbox-search" class="visually-hidden">{{ 'approvals.inbox.search' | translate }}</label>
        <app-icon name="search" [size]="16" class="field-icon" />
        <input
          id="inbox-search"
          type="search"
          [value]="query().q ?? ''"
          [attr.maxlength]="maxSearch"
          [placeholder]="'approvals.inbox.searchPlaceholder' | translate"
          (input)="search$.next($any($event.target).value)"
        />
      </div>
      <div class="field">
        <label for="inbox-sort">{{ 'requests.list.sortBy' | translate }}</label>
        <select id="inbox-sort" (change)="onSort($any($event.target).value)">
          @for (sort of sorts; track sort) {
            <option [value]="sort" [selected]="query().sort === sort">{{ 'approvals.inbox.sorts.' + sort | translate }}</option>
          }
        </select>
      </div>
    </div>

    <p class="visually-hidden" role="status" aria-live="polite">
      @if (state() === 'loading') {
        {{ 'approvals.inbox.loading' | translate }}
      } @else if (state() === 'ready') {
        {{ 'approvals.inbox.resultCount' | translate: { count: total() } }}
      }
    </p>

    @switch (state()) {
      @case ('loading') {
        <div class="cards">
          @for (row of [1, 2, 3]; track row) {
            <app-skeleton height="6.5rem" />
          }
        </div>
      }
      @case ('error') {
        <app-error-state
          [heading]="'approvals.inbox.loadError' | translate"
          [message]="'errors.' + (error() ?? 'unexpected-error') | translate"
          [retryLabel]="'common.retry' | translate"
          (retry)="load()"
        />
      }
      @default {
        @if (items().length === 0) {
          <app-empty-state
            icon="check-check"
            [heading]="(query().q ? 'approvals.inbox.noMatch.title' : 'approvals.inbox.empty.' + query().view + '.title') | translate"
            [message]="(query().q ? 'approvals.inbox.noMatch.message' : 'approvals.inbox.empty.' + query().view + '.message') | translate"
          />
        } @else {
          <div class="workspace" [class.is-split]="split()">
          <ul class="cards">
            @for (item of items(); track item.taskId) {
              <li class="card" [class.is-overdue-card]="isOverdue(item)" [class.is-selected]="split() && selected()?.taskId === item.taskId" [class.is-leaving]="leaving() === item.taskId">
                <div class="card-main">
                  <p class="card-number tabular"><bdi>{{ item.number ?? '—' }}</bdi></p>
                  <h2 class="card-title">
                    <a [routerLink]="['/requests', item.requestId]">{{ item.title }}</a>
                  </h2>
                  <p class="card-meta">
                    {{ item.requester?.displayName ?? '' }}
                    @if (item.company) {
                      · {{ item.company.name }}
                    }
                    @if (item.stageName) {
                      · {{ item.stageName }}
                    }
                  </p>
                  <div class="badges">
                    @if (item.questionOpen) {
                      <app-status-badge tone="warning">{{ 'approvals.inbox.informationRequested' | translate }}</app-status-badge>
                    }
                    @if (item.onBehalfOf) {
                      <app-status-badge tone="info">{{ 'tracker.onBehalfOf' | translate: { name: item.onBehalfOf.displayName } }}</app-status-badge>
                    }
                    @if (query().view === 'decided') {
                      <app-status-badge [tone]="item.status === 'APPROVED' ? 'success' : item.status === 'REJECTED' ? 'danger' : 'neutral'">
                        {{ 'tracker.taskStatus.' + item.status | translate }}
                      </app-status-badge>
                    }
                  </div>
                </div>
                <div class="card-side">
                  <p class="amount tabular"><bdi>{{ money(item.amount, item.currency) }}</bdi></p>
                  @if (item.companyCurrency && item.companyCurrency !== item.currency && item.companyAmount !== null && item.companyAmount !== undefined) {
                    <p class="amount-secondary tabular"><bdi>≈ {{ money(item.companyAmount, item.companyCurrency) }}</bdi></p>
                  }
                  @if (query().view === 'pending') {
                    <p class="when">{{ 'approvals.inbox.waiting' | translate: { elapsed: elapsed(item.waitingSince) } }}</p>
                    @if (item.dueAt) {
                      <p class="when due-line" [class.is-overdue]="isOverdue(item)">
                        @if (isOverdue(item)) {
                          <app-icon name="triangle-alert" [size]="14" />
                        }
                        {{ (isOverdue(item) ? 'tracker.overdue' : 'tracker.due') | translate: { when: until(item.dueAt) } }}
                      </p>
                    }
                    @if (split()) {
                      <button class="row-select" type="button" [attr.aria-pressed]="selected()?.taskId === item.taskId" (click)="select(item)">
                        <span class="visually-hidden">{{ 'approvals.inbox.showDecision' | translate }}: {{ item.title }}</span>
                      </button>
                    } @else {
                      <a appButton size="small" [routerLink]="['/requests', item.requestId]">{{ 'approvals.inbox.review' | translate }}</a>
                    }
                  } @else if (item.decidedAt) {
                    <p class="when"><time [attr.datetime]="item.decidedAt">{{ item.decidedAt | date: 'medium' : undefined : language.locale() }}</time></p>
                  }
                </div>
              </li>
            }
          </ul>
          @if (split() && selected(); as item) {
            <section class="pane" [attr.aria-label]="'approvals.decide.title' | translate">
              <p class="card-number tabular"><bdi>{{ item.number ?? '—' }}</bdi></p>
              <h2 class="pane-title">{{ item.title }}</h2>
              <dl class="facts">
                <div>
                  <dt>{{ 'approvals.inbox.facts.amount' | translate }}</dt>
                  <dd class="tabular"><bdi>{{ money(item.amount, item.currency) }}</bdi></dd>
                </div>
                <div>
                  <dt>{{ 'approvals.inbox.facts.requester' | translate }}</dt>
                  <dd>{{ item.requester?.displayName ?? '—' }}</dd>
                </div>
                <div>
                  <dt>{{ 'approvals.inbox.facts.waiting' | translate }}</dt>
                  <dd>{{ elapsed(item.waitingSince) }}</dd>
                </div>
                <div>
                  <dt>{{ 'approvals.inbox.facts.due' | translate }}</dt>
                  <dd [class.is-overdue]="isOverdue(item)">{{ item.dueAt ? until(item.dueAt) : '—' }}</dd>
                </div>
              </dl>
              @if (paneState() === 'loading') {
                <app-skeleton height="6rem" />
              } @else if (paneState() === 'error') {
                <app-error-state [heading]="'approvals.inbox.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="loadPane(item)" />
              } @else {
                <app-approval-actions [requestId]="item.requestId" [approval]="approval()" (changed)="onDecided(item)" />
              }
              <a appButton variant="secondary" [routerLink]="['/requests', item.requestId]">{{ 'approvals.inbox.openRecord' | translate }}</a>
            </section>
          }
          </div>

          @if (pages() > 1) {
            <nav class="pagination" [attr.aria-label]="'requests.list.pagination' | translate">
              <button appButton variant="secondary" size="small" type="button" [disabled]="query().page === 0" (click)="navigate({ page: query().page - 1 })">
                <app-icon name="chevron-left" [size]="16" />
                {{ 'requests.list.previous' | translate }}
              </button>
              <span class="tabular">{{ query().page + 1 }} / {{ pages() }}</span>
              <button appButton variant="secondary" size="small" type="button" [disabled]="query().page + 1 >= pages()" (click)="navigate({ page: query().page + 1 })">
                {{ 'requests.list.next' | translate }}
                <app-icon name="chevron-right" [size]="16" />
              </button>
            </nav>
          }
        }
      }
    }
  `,
  styleUrl: './approvals-inbox.scss',
})
export class ApprovalsInboxPage {
  private readonly api = inject(ApprovalsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly language = inject(LanguageService);
  protected readonly count = inject(ApprovalCountService);

  protected readonly views: InboxView[] = ['pending', 'decided'];
  protected readonly sorts = INBOX_SORTS;
  protected readonly maxSearch = MAX_SEARCH_LENGTH;
  protected readonly search$ = new Subject<string>();

  protected readonly query = signal<InboxQuery>(inboxQueryFromParams(this.route.snapshot.queryParamMap));
  protected readonly state = signal<PageState>('loading');
  protected readonly error = signal<string | null>(null);
  private readonly page = signal<Page<InboxItem> | null>(null);
  protected readonly items = computed(() => this.page()?.items ?? []);
  protected readonly total = computed(() => this.page()?.totalItems ?? 0);
  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / PAGE_SIZE)));
  private readonly now = signal(Date.now());

  private readonly reload$ = new Subject<void>();
  private readonly paneLoad$ = new Subject<InboxItem>();

  private readonly wide = toSignal(inject(BreakpointObserver).observe('(min-width: 64rem)').pipe(map((r) => r.matches)), {
    initialValue: false,
  });
  /** Queue beside the decision on wide screens; on phones each row opens the record as before. */
  protected readonly split = computed(() => this.wide() && this.query().view === 'pending');
  private readonly chosenId = signal<number | null>(null);
  protected readonly selected = computed(
    () => this.items().find((i) => i.taskId === this.chosenId()) ?? this.items()[0] ?? null,
  );
  protected readonly approval = signal<ApprovalView | null>(null);
  protected readonly paneState = signal<PageState>('loading');
  protected readonly leaving = signal<number | null>(null);
  private paneKey: number | null = null;

  constructor() {
    const destroyRef = inject(DestroyRef);
    const ticker = setInterval(() => this.now.set(Date.now()), 60_000);
    destroyRef.onDestroy(() => clearInterval(ticker));

    // One in-flight request at a time: a filter change cancels the previous load, so a slow unfiltered response can
    // never overwrite the filtered page that arrived first (QA2-D-11). Subscribed before the query stream, which
    // emits synchronously.
    this.reload$
      .pipe(
        switchMap(() => {
          this.state.set('loading');
          return this.api.inbox(this.query()).pipe(
            map((page): LoadResult => ({ kind: 'ready', page })),
            catchError((error: unknown) => of<LoadResult>({ kind: 'error', problem: toApiProblem(error) })),
          );
        }),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        if (result.kind === 'ready') {
          this.page.set(result.page);
          this.state.set('ready');
        } else if (result.problem.status !== 401) {
          this.error.set(result.problem.code);
          this.state.set('error');
        }
      });

    this.paneLoad$
      .pipe(
        switchMap((item) => {
          this.paneState.set('loading');
          return this.api.approval(item.requestId).pipe(
            map((view): ApprovalView | null => view),
            catchError(() => of('failed' as const)),
          );
        }),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        if (result === 'failed') {
          this.paneState.set('error');
        } else {
          this.approval.set(result);
          this.paneState.set('ready');
        }
      });

    effect(() => {
      const item = this.split() ? this.selected() : null;
      if (item && item.taskId !== this.paneKey) {
        this.paneKey = item.taskId;
        this.paneLoad$.next(item);
      } else if (!item) {
        this.paneKey = null;
      }
    });

    this.route.queryParamMap.pipe(takeUntilDestroyed(destroyRef)).subscribe((params) => {
      this.query.set(inboxQueryFromParams(params));
      this.load();
    });
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(destroyRef))
      .subscribe((q) => this.navigate({ q: q.trim().slice(0, MAX_SEARCH_LENGTH) || null, page: null }));
  }

  load(): void {
    this.reload$.next();
    if (this.query().view === 'pending') {
      this.count.refresh();
    }
  }

  protected select(item: InboxItem): void {
    this.chosenId.set(item.taskId);
  }

  protected loadPane(item: InboxItem): void {
    this.paneLoad$.next(item);
  }

  /** The decision is already saved: the row leaves, then the queue reloads and the next item takes the pane. */
  protected onDecided(item: InboxItem): void {
    this.leaving.set(item.taskId);
    setTimeout(() => {
      this.leaving.set(null);
      this.chosenId.set(null);
      this.paneKey = null;
      this.load();
    }, DECISION_EXIT_MS);
  }

  protected navigate(changes: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: changes, queryParamsHandling: 'merge' });
  }

  protected onSort(sort: string): void {
    this.navigate({
      sort,
      dir: sort === 'requiredDate' || sort === 'dueAt' ? 'asc' : 'desc',
      page: null,
    });
  }

  protected isOverdue(item: InboxItem): boolean {
    return item.status === 'PENDING' && (!!item.overdue || (!!item.dueAt && Date.parse(item.dueAt) < this.now()));
  }

  protected elapsed(iso: string): string {
    return elapsedText(iso, this.now(), this.language.locale());
  }

  protected until(iso: string): string {
    return untilText(iso, this.now(), this.language.locale());
  }

  protected money(amount: string | number | null | undefined, currency: string): string {
    return formatMoney(amount ?? null, currency, minorUnitsOf(currency), this.language.locale()) || '—';
  }
}
