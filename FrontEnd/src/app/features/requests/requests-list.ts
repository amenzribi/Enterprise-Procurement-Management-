import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { formatMoney, minorUnitsOf } from './money';
import {
  Page,
  RequestListItem,
  RequestQuery,
  SORTABLE_COLUMNS,
  SortColumn,
  SortDirection,
} from './request.models';
import { USER_STATUSES, statusKey, userStatusKey, userStatusOf, userStatusTone } from './request-status';
import { RequestsApi } from './requests.api';

export const PAGE_SIZE = 20;
/** Same limit as the server's `q` parameter. */
export const MAX_SEARCH_LENGTH = 100;

type ListState =
  | { kind: 'loading' }
  | { kind: 'error'; code: string }
  | { kind: 'loaded'; page: Page<RequestListItem> };

/** Minor units of the currencies seen in demo data; others fall back to 2 for list display. */

/** Reads list state from the URL so filters, sorting and paging survive reload and back navigation. */
export function queryFromParams(params: ParamMap): RequestQuery {
  const sort = params.get('sort');
  const direction = params.get('dir');
  const page = Number(params.get('page'));
  const status = params.get('status');
  return {
    scope: params.get('scope') === 'visible' ? 'visible' : 'mine',
    status: status && (USER_STATUSES as readonly string[]).includes(status) ? status : null,
    q: params.get('q')?.trim().slice(0, MAX_SEARCH_LENGTH) || null,
    sort: (SORTABLE_COLUMNS as readonly string[]).includes(sort ?? '') ? (sort as SortColumn) : 'createdAt',
    direction: direction === 'asc' ? 'asc' : 'desc',
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZE,
  };
}

@Component({
  selector: 'app-requests-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  templateUrl: './requests-list.html',
  styleUrl: './requests-list.scss',
})
export class RequestsListPage {
  private readonly api = inject(RequestsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly language = inject(LanguageService);
  private readonly auth = inject(AuthService);

  /** Features follow permissions from the server, never role names. */
  protected readonly canCreate = computed(() => hasPermission(this.auth.me(), 'REQUEST_CREATE'));
  protected readonly canSeeOthers = computed(
    () => hasPermission(this.auth.me(), 'REQUEST_VIEW_DEPARTMENT') || hasPermission(this.auth.me(), 'REQUEST_VIEW_GROUP'),
  );

  /** The filter offers the twelve user-facing statuses (Solution Guide v3.0 §8). */
  protected readonly statuses = USER_STATUSES;
  protected readonly maxSearchLength = MAX_SEARCH_LENGTH;
  protected readonly columns: { key: SortColumn; label: string; numeric?: boolean }[] = [
    { key: 'number', label: 'requests.list.columns.number' },
    { key: 'title', label: 'requests.list.columns.title' },
    { key: 'status', label: 'requests.list.columns.status' },
    { key: 'totalAmount', label: 'requests.list.columns.total', numeric: true },
    { key: 'requiredDate', label: 'requests.list.columns.requiredDate' },
    { key: 'createdAt', label: 'requests.list.columns.createdAt' },
    { key: 'updatedAt', label: 'requests.list.columns.updatedAt' },
  ];

  protected readonly isNarrow = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 47.99rem)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );

  protected readonly query = toSignal(this.route.queryParamMap.pipe(map(queryFromParams)), {
    initialValue: queryFromParams(this.route.snapshot.queryParamMap),
  });

  protected readonly state = signal<ListState>({ kind: 'loading' });
  protected readonly searchText = signal(this.route.snapshot.queryParamMap.get('q') ?? '');
  private readonly search$ = new Subject<string>();
  private readonly reload$ = new Subject<void>();

  protected readonly summary = computed(() => {
    const state = this.state();
    if (state.kind !== 'loaded' || state.page.totalItems === 0) {
      return null;
    }
    const { page, size, totalItems, items } = state.page;
    return { from: page * size + 1, to: page * size + items.length, total: totalItems };
  });

  protected readonly page = computed(() => {
    const state = this.state();
    return state.kind === 'loaded' ? state.page : null;
  });

  protected readonly errorCode = computed(() => {
    const state = this.state();
    return state.kind === 'error' ? state.code : null;
  });

  /** "Visible to me" only for users allowed to see other people's requests. */
  protected readonly scope = computed(() => (this.canSeeOthers() ? this.query().scope : 'mine'));

  protected readonly hasFilters = computed(() => !!this.query().status || !!this.query().q);

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Subscribe the loader first: the query parameter stream emits synchronously on subscription.
    this.reload$
      .pipe(
        switchMap(() => {
          this.state.set({ kind: 'loading' });
          return this.api.list(this.effectiveQuery()).pipe(
            map((page): ListState => ({ kind: 'loaded', page: { ...page, items: page.items ?? [] } })),
            // Errors stay inside the inner stream so a later retry or filter change still loads.
            catchError((error: unknown) => of<ListState>({ kind: 'error', code: toApiProblem(error).code })),
          );
        }),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((state) => this.state.set(state));

    this.route.queryParamMap
      .pipe(
        map(queryFromParams),
        distinctUntilChanged((a, b) => JSON.stringify(a) === JSON.stringify(b)),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe(() => this.reload$.next());

    this.search$
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(destroyRef))
      .subscribe((q) => this.navigate({ q: q.trim() || null, page: null }));
  }

  protected retry(): void {
    this.reload$.next();
  }

  protected selectScope(scope: 'mine' | 'visible'): void {
    this.navigate({ scope: scope === 'mine' ? null : scope, page: null });
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    this.search$.next(value);
  }

  protected onStatus(value: string): void {
    this.navigate({ status: value || null, page: null });
  }

  protected sortBy(column: SortColumn): void {
    const current = this.query();
    const direction: SortDirection = current.sort === column && current.direction === 'asc' ? 'desc' : 'asc';
    this.navigate({ sort: column, dir: direction, page: null });
  }

  protected onSortSelect(value: string): void {
    const [sort, dir] = value.split(',');
    this.navigate({ sort, dir, page: null });
  }

  protected goToPage(page: number): void {
    this.navigate({ page: page > 0 ? page : null });
  }

  protected clearFilters(): void {
    this.searchText.set('');
    this.navigate({ q: null, status: null, page: null });
  }

  protected ariaSort(column: SortColumn): 'ascending' | 'descending' | null {
    const query = this.query();
    if (query.sort !== column) {
      return null;
    }
    return query.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected tone(item: RequestListItem) {
    return userStatusTone(userStatusOf(item));
  }

  /** Label of a request's user-facing status (or of a filter value). */
  protected statusLabel(item: RequestListItem | string): string {
    if (typeof item === 'string') {
      return userStatusKey(item);
    }
    const status = userStatusOf(item);
    return status ? userStatusKey(status) : statusKey(item.status);
  }

  protected total(item: RequestListItem): string {
    return formatMoney(item.totalAmount ?? null, item.currency, minorUnitsOf(item.currency), this.locale());
  }

  protected locale(): string {
    return this.language.locale();
  }

  private effectiveQuery(): RequestQuery {
    return { ...this.query(), scope: this.scope() };
  }

  private navigate(changes: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: changes,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
