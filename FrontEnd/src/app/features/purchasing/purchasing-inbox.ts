import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { formatMoney, minorUnitsOf } from '../requests/money';
import { Page } from '../requests/request.models';
import { userStatusTone } from '../requests/request-status';
import { PurchasingCountService } from './purchasing-count.service';
import { QUEUE_SORTS, QueueFilterOptions, QueueItem, QueueQuery, QueueSort } from './purchasing.models';
import { PurchasingApi } from './purchasing.api';

type PageState = 'loading' | 'ready' | 'error';
type LoadResult = { kind: 'ready'; page: Page<QueueItem> } | { kind: 'error'; problem: ReturnType<typeof toApiProblem> };

const PAGE_SIZE = 20;
const MAX_SEARCH_LENGTH = 100;
const STATUSES = ['IN_PURCHASING_REVIEW', 'ASSIGNED_TO_BUYER', 'PURCHASING_RETURNED'] as const;
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
const MODES = ['DIRECT', 'CENTRALIZED', 'ON_BEHALF'] as const;

function optionalId(params: ParamMap, key: string): number | null {
  const raw = params.get(key);
  if (!raw) {
    return null;
  }
  const id = Number(raw);
  return Number.isInteger(id) && id > 0 ? id : null;
}

/** Reads the queue query from the URL. Exported for tests. */
export function queueQueryFromParams(params: ParamMap): QueueQuery {
  const sort = params.get('sort');
  const page = Number(params.get('page'));
  const complete = params.get('complete');
  return {
    q: params.get('q')?.trim().slice(0, MAX_SEARCH_LENGTH) || null,
    status: STATUSES.includes(params.get('status') as (typeof STATUSES)[number]) ? params.get('status') : null,
    priority: PRIORITIES.includes(params.get('priority') as (typeof PRIORITIES)[number]) ? params.get('priority') : null,
    purchaseMode: MODES.includes(params.get('mode') as (typeof MODES)[number]) ? params.get('mode') : null,
    buyingCompanyId: optionalId(params, 'company'),
    categoryId: optionalId(params, 'category'),
    countryCode: params.get('country')?.trim().toUpperCase().slice(0, 2) || null,
    buyerUserId: optionalId(params, 'buyer'),
    requesterUserId: optionalId(params, 'requester'),
    requiredFrom: params.get('requiredFrom') || null,
    requiredTo: params.get('requiredTo') || null,
    amountMin: params.get('amountMin') || null,
    amountMax: params.get('amountMax') || null,
    complete: complete === 'true' ? true : complete === 'false' ? false : null,
    sort: (QUEUE_SORTS as readonly string[]).includes(sort ?? '') ? (sort as QueueSort) : 'submittedAt',
    direction: params.get('dir') === 'asc' ? 'asc' : 'desc',
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZE,
  };
}

/** Completeness gap labels joined for a card. Exported for tests. */
export function gapList(item: QueueItem): string {
  return (item.completenessGaps ?? []).join(', ');
}

/**
 * Buying-company queue of approved requests (S3-AC-01). Search cancels the previous load so a slow unfiltered
 * response cannot overwrite a filtered page (same rule as QA2-D-11).
 */
@Component({
  selector: 'app-purchasing-inbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'purchasing.inbox.title' | translate" [description]="'purchasing.inbox.description' | translate" />

    <div class="toolbar" role="search" [attr.aria-label]="'purchasing.inbox.filtersLabel' | translate">
      <div class="field">
        <label for="queue-search" class="visually-hidden">{{ 'purchasing.inbox.search' | translate }}</label>
        <app-icon name="search" [size]="16" class="field-icon" />
        <input
          id="queue-search"
          type="search"
          [value]="query().q ?? ''"
          [attr.maxlength]="maxSearch"
          [placeholder]="'purchasing.inbox.searchPlaceholder' | translate"
          (input)="search$.next($any($event.target).value)"
        />
      </div>
      <div class="field">
        <label for="queue-status">{{ 'purchasing.inbox.status' | translate }}</label>
        <select id="queue-status" (change)="navigate({ status: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().status">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (status of statuses; track status) {
            <option [value]="status" [selected]="query().status === status">{{ 'requests.status.' + status | translate }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-priority">{{ 'purchasing.inbox.priority' | translate }}</label>
        <select id="queue-priority" (change)="navigate({ priority: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().priority">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (priority of priorities; track priority) {
            <option [value]="priority" [selected]="query().priority === priority">{{ 'requests.priority.' + priority | translate }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-sort">{{ 'requests.list.sortBy' | translate }}</label>
        <select id="queue-sort" (change)="onSort($any($event.target).value)">
          @for (sort of sorts; track sort) {
            <option [value]="sort" [selected]="query().sort === sort">{{ 'purchasing.sorts.' + sort | translate }}</option>
          }
        </select>
      </div>
      <details class="more" [open]="advancedOpen()" (toggle)="advancedOpen.set($any($event.target).open)">
        <summary>
          {{ 'purchasing.inbox.moreFilters' | translate }}
          @if (advancedCount() > 0) {
            <span class="tabular">({{ advancedCount() }})</span>
          }
        </summary>
        <div class="more-fields">
      <div class="field">
        <label for="queue-mode">{{ 'purchasing.inbox.mode' | translate }}</label>
        <select id="queue-mode" (change)="navigate({ mode: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().purchaseMode">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (mode of modes; track mode) {
            <option [value]="mode" [selected]="query().purchaseMode === mode">{{ 'purchasing.mode.' + mode | translate }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-company">{{ 'purchasing.inbox.company' | translate }}</label>
        <select id="queue-company" (change)="navigate({ company: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().buyingCompanyId">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (company of options()?.companies ?? []; track company.id) {
            <option [value]="company.id" [selected]="query().buyingCompanyId === company.id">{{ company.name }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-country">{{ 'purchasing.inbox.country' | translate }}</label>
        <select id="queue-country" (change)="navigate({ country: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().countryCode">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (country of options()?.countries ?? []; track country) {
            <option [value]="country" [selected]="query().countryCode === country">{{ country }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-category">{{ 'purchasing.inbox.category' | translate }}</label>
        <select id="queue-category" (change)="navigate({ category: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().categoryId">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (category of options()?.categories ?? []; track category.id) {
            <option [value]="category.id" [selected]="query().categoryId === category.id">{{ category.name }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-buyer">{{ 'purchasing.inbox.buyer' | translate }}</label>
        <select id="queue-buyer" (change)="navigate({ buyer: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().buyerUserId">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (buyer of options()?.buyers ?? []; track buyer.id) {
            <option [value]="buyer.id" [selected]="query().buyerUserId === buyer.id">{{ buyer.name }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-requester">{{ 'purchasing.inbox.requester' | translate }}</label>
        <select id="queue-requester" (change)="navigate({ requester: $any($event.target).value || null, page: null })">
          <option value="" [selected]="!query().requesterUserId">{{ 'purchasing.inbox.all' | translate }}</option>
          @for (requester of options()?.requesters ?? []; track requester.id) {
            <option [value]="requester.id" [selected]="query().requesterUserId === requester.id">{{ requester.name }}</option>
          }
        </select>
      </div>
      <div class="field">
        <label for="queue-complete">{{ 'purchasing.inbox.completeness' | translate }}</label>
        <select id="queue-complete" (change)="navigate({ complete: $any($event.target).value || null, page: null })">
          <option value="" [selected]="query().complete === null">{{ 'purchasing.inbox.all' | translate }}</option>
          <option value="true" [selected]="query().complete === true">{{ 'purchasing.inbox.completeYes' | translate }}</option>
          <option value="false" [selected]="query().complete === false">{{ 'purchasing.inbox.completeNo' | translate }}</option>
        </select>
      </div>
      <div class="field">
        <label for="queue-required-from">{{ 'purchasing.inbox.requiredFrom' | translate }}</label>
        <input id="queue-required-from" type="date" [value]="query().requiredFrom ?? ''" (change)="navigate({ requiredFrom: $any($event.target).value || null, page: null })" />
      </div>
      <div class="field">
        <label for="queue-required-to">{{ 'purchasing.inbox.requiredTo' | translate }}</label>
        <input id="queue-required-to" type="date" [value]="query().requiredTo ?? ''" (change)="navigate({ requiredTo: $any($event.target).value || null, page: null })" />
      </div>
      <div class="field">
        <label for="queue-amount-min">{{ 'purchasing.inbox.amountMin' | translate }}</label>
        <input id="queue-amount-min" type="number" min="0" step="0.01" [value]="query().amountMin ?? ''" (change)="navigate({ amountMin: $any($event.target).value || null, page: null })" />
      </div>
      <div class="field">
        <label for="queue-amount-max">{{ 'purchasing.inbox.amountMax' | translate }}</label>
        <input id="queue-amount-max" type="number" min="0" step="0.01" [value]="query().amountMax ?? ''" (change)="navigate({ amountMax: $any($event.target).value || null, page: null })" />
      </div>
        </div>
      </details>
    </div>

    <p class="visually-hidden" role="status" aria-live="polite">
      @if (state() === 'loading') {
        {{ 'purchasing.inbox.loading' | translate }}
      } @else if (state() === 'ready') {
        {{ 'purchasing.inbox.resultCount' | translate: { count: total() } }}
      }
    </p>

    @switch (state()) {
      @case ('loading') {
        <app-skeleton height="6rem" />
        <app-skeleton height="6rem" />
      }
      @case ('error') {
        <app-error-state
          [heading]="'purchasing.inbox.loadError' | translate"
          [message]="'errors.' + (error() ?? 'unexpected-error') | translate"
          [retryLabel]="'common.retry' | translate"
          (retry)="load()"
        />
      }
      @default {
        @if (items().length === 0) {
          <app-empty-state icon="inbox" [heading]="'purchasing.inbox.empty.title' | translate" [message]="'purchasing.inbox.empty.message' | translate" />
        } @else {
          <ul class="cards">
            @for (item of items(); track item.requestId) {
              <li class="card">
                <div class="card-main">
                  <p class="card-number tabular"><bdi>{{ item.number }}</bdi></p>
                  <p class="card-title">
                    <a [routerLink]="['/requests', item.requestId]">{{ item.title }}</a>
                  </p>
                  <p class="card-meta">
                    {{ item.requester?.name }}
                    @if (item.requestingCompany?.name) {
                      · {{ item.requestingCompany?.name }}
                    }
                  </p>
                  <div class="badges">
                    <app-status-badge [tone]="tone(item)">{{ 'requests.userStatus.' + item.userStatus | translate }}</app-status-badge>
                    @if (item.priority === 'URGENT') {
                      <app-status-badge tone="danger">{{ 'requests.priority.URGENT' | translate }}</app-status-badge>
                    }
                    @if (item.assignedBuyer?.name) {
                      <app-status-badge tone="info">{{ 'purchasing.inbox.assignedTo' | translate: { name: item.assignedBuyer.name } }}</app-status-badge>
                    } @else {
                      <app-status-badge tone="neutral">{{ 'purchasing.inbox.unassigned' | translate }}</app-status-badge>
                    }
                    @if (item.buyingCompanyProposed) {
                      <app-status-badge tone="warning">{{ 'purchasing.inbox.proposed' | translate }}</app-status-badge>
                    }
                  </div>
                  @if (item.completenessGaps?.length) {
                    <p class="card-meta">{{ 'purchasing.inbox.gaps' | translate: { list: gapList(item) } }}</p>
                  }
                </div>
                <div class="card-side">
                  <p class="amount tabular">{{ money(item.amount, item.currency) }}</p>
                  @if (item.requiredDate) {
                    <p class="when"><time [attr.datetime]="item.requiredDate">{{ item.requiredDate | date: 'mediumDate' : undefined : language.locale() }}</time></p>
                  }
                  <a appButton size="small" [routerLink]="['/requests', item.requestId]">{{ 'purchasing.inbox.open' | translate }}</a>
                </div>
              </li>
            }
          </ul>
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
  styleUrl: '../approvals/approvals-inbox.scss',
})
export class PurchasingInboxPage {
  private readonly api = inject(PurchasingApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly language = inject(LanguageService);
  private readonly count = inject(PurchasingCountService);

  protected readonly sorts = QUEUE_SORTS;
  protected readonly statuses = STATUSES;
  protected readonly priorities = PRIORITIES;
  protected readonly modes = MODES;
  protected readonly maxSearch = MAX_SEARCH_LENGTH;
  protected readonly search$ = new Subject<string>();

  protected readonly query = signal<QueueQuery>(queueQueryFromParams(this.route.snapshot.queryParamMap));
  protected readonly options = signal<QueueFilterOptions | null>(null);
  protected readonly state = signal<PageState>('loading');
  protected readonly error = signal<string | null>(null);
  private readonly page = signal<Page<QueueItem> | null>(null);
  protected readonly items = computed(() => this.page()?.items ?? []);
  protected readonly total = computed(() => this.page()?.totalItems ?? 0);
  protected readonly pages = computed(() => Math.max(1, Math.ceil(this.total() / PAGE_SIZE)));

  private readonly reload$ = new Subject<void>();

  protected readonly advancedOpen = signal(false);

  constructor() {
    this.advancedOpen.set(this.advancedCount() > 0);
    const destroyRef = inject(DestroyRef);
    this.api.filterOptions().pipe(takeUntilDestroyed(destroyRef)).subscribe({
      next: (options) => this.options.set(options),
      error: () => this.options.set({ companies: [], categories: [], buyers: [], requesters: [], countries: [] }),
    });
    this.reload$
      .pipe(
        switchMap(() => {
          this.state.set('loading');
          return this.api.queue(this.query()).pipe(
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

    this.route.queryParamMap.pipe(takeUntilDestroyed(destroyRef)).subscribe((params) => {
      this.query.set(queueQueryFromParams(params));
      this.load();
    });
    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(destroyRef))
      .subscribe((q) => this.navigate({ q: q.trim().slice(0, MAX_SEARCH_LENGTH) || null, page: null }));
  }

  load(): void {
    this.reload$.next();
    this.count.refresh();
  }

  /** Filters folded under "More filters"; open on arrival when one is applied, then left as the user sets it. */
  protected advancedCount(): number {
    const q = this.query();
    return [q.purchaseMode, q.buyingCompanyId, q.countryCode, q.categoryId, q.buyerUserId, q.requesterUserId, q.complete, q.requiredFrom, q.requiredTo, q.amountMin, q.amountMax].filter(
      (value) => value !== null && value !== undefined && value !== '',
    ).length;
  }

  protected navigate(changes: Record<string, string | number | null>): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: changes, queryParamsHandling: 'merge' });
  }

  protected onSort(sort: string): void {
    this.navigate({ sort, dir: sort === 'requiredDate' ? 'asc' : 'desc', page: null });
  }

  protected tone(item: QueueItem) {
    return userStatusTone(item.userStatus as never);
  }

  protected gapList = gapList;

  protected money(amount: string | number | null | undefined, currency: string): string {
    return formatMoney(amount ?? null, currency, minorUnitsOf(currency), this.language.locale()) || '—';
  }
}
