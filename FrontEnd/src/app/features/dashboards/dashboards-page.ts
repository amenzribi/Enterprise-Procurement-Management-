import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, forkJoin, map, of } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { AuthService } from '../../core/auth/auth.service';
import { hasPermission } from '../../core/auth/auth.models';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { isUserStatus, userStatusTone } from '../requests/request-status';
import { DashboardsApi } from './dashboards.api';
import { ManagementPage, PipelineView } from './dashboards.models';

type PageState = 'loading' | 'ready' | 'error';

/** Lifecycle phases shown on the dashboard, each a set of user-facing statuses (requests/request-status.ts). */
const PHASES = [
  { key: 'preparation', statuses: ['DRAFT', 'RETURNED'] },
  { key: 'approval', statuses: ['SUBMITTED'] },
  { key: 'purchasing', statuses: ['APPROVED', 'ASSIGNED', 'QUOTING', 'AWARDED'] },
  { key: 'delivery', statuses: ['ORDERED', 'IN_TRANSIT', 'RECEIVED'] },
  { key: 'finished', statuses: ['CLOSED', 'REJECTED', 'CANCELLED'] },
] as const;

function triggerDownload(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

@Component({
  selector: 'app-dashboards-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'dashboards.title' | translate" [description]="'dashboards.description' | translate">
      @if (canExport()) {
        <button appButton variant="secondary" type="button" [disabled]="exporting()" (click)="exportRequests()">
          <app-icon name="download" [size]="16" />
          {{ 'dashboards.exportRequests' | translate }}
        </button>
        <button appButton variant="secondary" type="button" [disabled]="exporting()" (click)="exportPos()">
          <app-icon name="download" [size]="16" />
          {{ 'dashboards.exportPos' | translate }}
        </button>
      }
    </app-page-header>

    @switch (state()) {
      @case ('loading') {
        <p class="visually-hidden" role="status">{{ 'dashboards.loading' | translate }}</p>
        <div class="loading">
          <app-skeleton height="6rem" />
          <app-skeleton height="12rem" />
        </div>
      }
      @case ('error') {
        <app-error-state
          [heading]="'dashboards.loadError' | translate"
          [message]="'errors.' + (error() ?? 'unexpected-error') | translate"
          [retryLabel]="'common.retry' | translate"
          (retry)="load()"
        />
      }
      @case ('ready') {
        @if (pipeline(); as pipe) {
          <section class="panel" aria-labelledby="pipeline-title">
            <h2 class="panel-title" id="pipeline-title">{{ 'dashboards.pipeline.title' | translate }}</h2>
            @if (pipe.byUserStatus.length === 0) {
              <app-empty-state
                icon="dashboard"
                [heading]="'dashboards.pipeline.emptyTitle' | translate"
                [message]="'dashboards.pipeline.emptyMessage' | translate"
              />
            } @else {
              <div class="pipeline">
                <!-- Where the requests in scope stand, by phase of the lifecycle. The figures beside it are the same data as text. -->
                <div class="donut-wrap">
                  <svg class="donut" viewBox="0 0 120 120" aria-hidden="true" focusable="false">
                    <circle class="donut-track" cx="60" cy="60" r="48" />
                    @for (phase of phases(); track phase.key) {
                      @if (phase.count > 0) {
                        <circle
                          class="donut-seg"
                          [class]="'donut-seg phase-' + phase.key"
                          cx="60"
                          cy="60"
                          r="48"
                          pathLength="100"
                          [attr.stroke-dasharray]="phase.dash"
                          [attr.stroke-dashoffset]="phase.offset"
                        />
                      }
                    }
                  </svg>
                  <div class="donut-center">
                    <p class="figure-value tabular">{{ pipelineTotal() }}</p>
                    <p class="figure-label">{{ 'dashboards.pipeline.total' | translate }}</p>
                  </div>
                </div>
                <div class="phase-side">
                  <h3 class="sub-title">{{ 'dashboards.pipeline.byPhase' | translate }}</h3>
                  <ul class="phases" role="list">
                    @for (phase of phases(); track phase.key) {
                      <li class="phase">
                        <span class="phase-swatch" [class]="'phase-swatch phase-' + phase.key" aria-hidden="true"></span>
                        <span class="phase-text">
                          <span class="phase-name">{{ 'dashboards.phases.' + phase.key + '.name' | translate }}</span>
                          <span class="phase-meaning">{{ 'dashboards.phases.' + phase.key + '.meaning' | translate }}</span>
                        </span>
                        <strong class="phase-count tabular">{{ phase.count }}</strong>
                        <span class="phase-share tabular">{{ phase.share }}%</span>
                      </li>
                    }
                  </ul>
                </div>
              </div>

              <h3 class="sub-title sub-title--spaced">{{ 'dashboards.pipeline.byStatus' | translate }}</h3>
              <!-- One bar per status; the count is always written, the bar only compares. Each row opens the list filtered to that status. -->
              <ul class="bars" role="list">
                @for (row of pipe.byUserStatus; track row.userStatus) {
                  <li>
                    <a class="bar-row bar-link" routerLink="/requests" [queryParams]="{ scope: 'visible', status: row.userStatus }">
                      <span class="bar-label">{{ 'requests.userStatus.' + row.userStatus | translate }}</span>
                      <span class="bar-track" aria-hidden="true"><span class="bar-fill" [style.inline-size.%]="share(row.count, pipelineMax())"></span></span>
                      <strong class="bar-value tabular">{{ row.count }}</strong>
                    </a>
                  </li>
                }
              </ul>
              @if (pipe.openAmountsByCurrency.length) {
                <p class="muted amounts-note">{{ 'dashboards.pipeline.amountsNote' | translate }}</p>
                <ul class="amounts" role="list">
                  @for (row of pipe.openAmountsByCurrency; track row.currency) {
                    <li>
                      <span class="amount-currency">{{ row.currency }}</span>
                      <strong class="amount-value tabular">{{ money(row.amount, row.currency) }}</strong>
                      <span class="muted">({{ row.requestCount }})</span>
                    </li>
                  }
                </ul>
              }
            }
          </section>

          @if (pipe.groupView) {
            <section class="panel" aria-labelledby="group-title">
              <h2 class="panel-title" id="group-title">{{ 'dashboards.group.title' | translate }}</h2>
              @if (pipe.byRequestingCompany.length === 0) {
                <p class="muted">{{ 'dashboards.group.empty' | translate }}</p>
              } @else {
                <ul class="bars" role="list">
                  @for (row of pipe.byRequestingCompany; track row.companyCode) {
                    <li class="bar-row bar-row--wide">
                      <span class="bar-label">{{ row.companyName }} <span class="muted">({{ row.companyCode }})</span></span>
                      <span class="bar-track" aria-hidden="true"><span class="bar-fill" [style.inline-size.%]="share(row.count, companyMax())"></span></span>
                      <strong class="bar-value tabular">{{ row.count }}</strong>
                    </li>
                  }
                </ul>
              }
            </section>
          }
        }

        <section class="panel" aria-labelledby="mgmt-title">
          <div class="title-row">
            <h2 class="panel-title" id="mgmt-title">{{ 'dashboards.management.title' | translate }}</h2>
            @if (lateOnPage() > 0) {
              <app-status-badge tone="danger">{{ 'dashboards.management.lateOnPage' | translate: { count: lateOnPage() } }}</app-status-badge>
            }
          </div>
          @if (rows().length === 0) {
            <app-empty-state
              icon="search"
              [heading]="'dashboards.management.emptyTitle' | translate"
              [message]="'dashboards.management.emptyMessage' | translate"
            />
          } @else {
            <div class="table-wrap" tabindex="0" role="region" [attr.aria-labelledby]="'mgmt-title'">
              <table class="table">
                <thead>
                  <tr>
                    <th scope="col">{{ 'dashboards.management.number' | translate }}</th>
                    <th scope="col">{{ 'dashboards.management.titleCol' | translate }}</th>
                    <th scope="col">{{ 'dashboards.management.status' | translate }}</th>
                    <th scope="col">{{ 'dashboards.management.owner' | translate }}</th>
                    <th scope="col">{{ 'dashboards.management.required' | translate }}</th>
                    <th scope="col" class="numeric">{{ 'dashboards.management.amount' | translate }}</th>
                    <th scope="col">{{ 'dashboards.management.late' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of rows(); track row.id) {
                    <tr>
                      <td class="number">
                        <a [routerLink]="['/requests', row.id]">{{ row.number ?? ('requests.list.draftNumber' | translate) }}</a>
                      </td>
                      <td class="title-cell">{{ row.title }}</td>
                      <td>
                        @if (row.userStatus) {
                          <app-status-badge [tone]="statusTone(row.userStatus)">{{ 'requests.userStatus.' + row.userStatus | translate }}</app-status-badge>
                        } @else {
                          —
                        }
                      </td>
                      <td>
                        {{ row.ownerName }}
                        @if (row.ownerSince) {
                          <span class="muted small">
                            <time [attr.datetime]="row.ownerSince">{{ row.ownerSince | date: 'medium' : undefined : locale() }}</time>
                          </span>
                        }
                      </td>
                      <td class="tabular">
                        @if (row.requiredDate) {
                          <time [attr.datetime]="row.requiredDate">{{ row.requiredDate | date: 'mediumDate' : undefined : locale() }}</time>
                        } @else {
                          —
                        }
                      </td>
                      <td class="numeric tabular">{{ money(row.totalAmount, row.currency) }}</td>
                      <td>
                        @if (row.late) {
                          <app-status-badge tone="danger">{{ 'dashboards.management.lateYes' | translate }}</app-status-badge>
                        } @else {
                          <span class="muted">{{ 'dashboards.management.lateNo' | translate }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (pages() > 1) {
              <nav class="pagination" [attr.aria-label]="'requests.list.pagination' | translate">
                <button appButton variant="secondary" size="small" type="button" [disabled]="page() === 0" (click)="go(page() - 1)">
                  {{ 'requests.list.previous' | translate }}
                </button>
                <span class="tabular">{{ page() + 1 }} / {{ pages() }}</span>
                <button
                  appButton
                  variant="secondary"
                  size="small"
                  type="button"
                  [disabled]="page() + 1 >= pages()"
                  (click)="go(page() + 1)"
                >
                  {{ 'requests.list.next' | translate }}
                </button>
              </nav>
            }
          }
        </section>
      }
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

    .panel {
      margin-block-end: var(--space-5);
    }

    .panel-title {
      margin: 0 0 var(--space-4);
      font-size: var(--text-lg);
    }

    /* The pipeline: the total inside a ring split by lifecycle phase, with the same figures written beside it. */
    .pipeline {
      display: grid;
      grid-template-columns: minmax(12rem, 16rem) minmax(0, 1fr);
      gap: var(--space-8);
      align-items: center;
    }

    .donut-wrap {
      position: relative;
      display: grid;
      place-items: center;
      aspect-ratio: 1;
    }

    .donut {
      inline-size: 100%;
      block-size: 100%;
      transform: rotate(-90deg);
    }

    .donut-track,
    .donut-seg {
      fill: none;
      stroke-width: 12;
    }

    .donut-track {
      stroke: var(--color-surface-sunken);
    }

    .donut-seg {
      stroke: var(--phase-color);
    }

    .phase-preparation { --phase-color: var(--chart-5); }
    .phase-approval { --phase-color: var(--chart-3); }
    .phase-purchasing { --phase-color: var(--chart-1); }
    .phase-delivery { --phase-color: var(--chart-2); }
    .phase-finished { --phase-color: var(--chart-4); }

    .donut-center {
      position: absolute;
      text-align: center;
      max-inline-size: 60%;
    }

    .figure-value {
      font-family: var(--font-display);
      font-size: 2.75rem;
      font-weight: 600;
      line-height: 1;
      letter-spacing: -0.02em;
    }

    .figure-label {
      margin-block-start: var(--space-1);
      color: var(--color-text-muted);
      font-size: var(--text-xs);
    }

    .sub-title {
      margin-block-end: var(--space-3);
      color: var(--color-text-muted);
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
      letter-spacing: 0;
    }

    .sub-title--spaced {
      margin-block-start: var(--space-6);
      padding-block-start: var(--space-5);
      border-block-start: 1px solid var(--color-line);
    }

    .phases {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .phase {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto 3.25rem;
      gap: var(--space-3);
      align-items: center;
      padding-block: var(--space-2);
      border-block-start: 1px solid var(--color-line);
    }

    .phase:first-child {
      border-block-start: 0;
    }

    .phase-swatch {
      inline-size: 0.75rem;
      block-size: 0.75rem;
      border-radius: 3px;
      background: var(--phase-color);
    }

    .phase-text {
      display: grid;
    }

    .phase-name {
      font-weight: var(--weight-semibold);
    }

    .phase-meaning {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
    }

    .phase-count {
      font-family: var(--font-display);
      font-size: var(--text-lg);
    }

    .phase-share {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
      text-align: end;
    }

    .title-row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      margin-block-end: var(--space-4);
    }

    .title-row .panel-title {
      margin: 0;
    }

    .bar-link {
      margin-inline: calc(-1 * var(--space-2));
      padding: var(--space-1) var(--space-2);
      border-radius: var(--radius-sm);
      color: inherit;
      text-decoration: none;
      transition: background-color var(--duration-fast) var(--ease-standard);
    }

    .bar-link:hover {
      background: var(--color-paper);
      color: inherit;
    }

    .bar-link:hover .bar-label {
      color: var(--color-accent);
      text-decoration: underline;
    }

    .bars,
    .amounts {
      list-style: none;
      margin: 0;
      padding: 0;
    }

    .bars {
      display: grid;
      gap: var(--space-2);
    }

    .bar-row {
      display: grid;
      grid-template-columns: minmax(7rem, 11rem) minmax(0, 1fr) 2.5rem;
      gap: var(--space-3);
      align-items: center;
      min-block-size: 1.75rem;
    }

    .bar-row--wide {
      grid-template-columns: minmax(9rem, 20rem) minmax(0, 1fr) 2.5rem;
    }

    .bar-label {
      overflow-wrap: anywhere;
    }

    .bar-track {
      display: block;
      block-size: 0.625rem;
      border-radius: var(--radius-pill);
      background: var(--color-surface-sunken);
      overflow: hidden;
    }

    .bar-fill {
      display: block;
      block-size: 100%;
      min-inline-size: 0.25rem;
      border-radius: var(--radius-pill);
      background: var(--color-accent);
    }

    .bar-value {
      text-align: end;
    }

    .amounts-note {
      margin-block: var(--space-5) var(--space-2);
      font-size: var(--text-sm);
    }

    .amounts {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-3);
    }

    .amounts li {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: baseline;
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
      background: var(--color-paper);
    }

    .amount-currency {
      color: var(--color-text-muted);
      font-size: var(--text-xs);
      font-weight: var(--weight-semibold);
    }

    .amount-value {
      font-family: var(--font-display);
      font-size: var(--text-lg);
    }

    .muted {
      color: var(--color-text-muted);
    }

    .small {
      font-size: var(--text-xs);
      display: block;
    }

    .table-wrap {
      overflow-x: auto;
      margin-inline: calc(-1 * var(--space-6));
    }

    .table {
      inline-size: 100%;
      border-collapse: collapse;
      font-size: var(--text-sm);
    }

    .table th,
    .table td {
      text-align: start;
      padding: var(--space-3) var(--space-4);
      border-block-end: 1px solid var(--color-line);
      vertical-align: middle;
    }

    .table th {
      padding-block: var(--space-2);
      background: var(--color-paper);
      white-space: nowrap;
    }

    .table th:first-child,
    .table td:first-child {
      padding-inline-start: var(--space-6);
    }

    .table th:last-child,
    .table td:last-child {
      padding-inline-end: var(--space-6);
    }

    .table tbody tr:hover {
      background: var(--color-paper);
    }

    .number {
      white-space: nowrap;
    }

    .number a {
      font-weight: var(--weight-semibold);
      text-decoration: none;
    }

    .title-cell {
      min-inline-size: 12rem;
      font-weight: var(--weight-medium);
    }

    .table .numeric {
      text-align: end;
      white-space: nowrap;
    }

    .tabular {
      font-variant-numeric: tabular-nums;
    }

    @media (max-width: 40rem) {
      .pipeline {
        grid-template-columns: minmax(0, 1fr);
        gap: var(--space-4);
      }

      .donut-wrap {
        max-inline-size: 13rem;
        margin-inline: auto;
      }

      .bar-row,
      .bar-row--wide {
        grid-template-columns: minmax(0, 1fr) 2.5rem;
      }

      .bar-track {
        grid-column: 1 / -1;
        grid-row: 2;
      }

      .table-wrap {
        margin-inline: calc(-1 * var(--space-4));
      }
    }

    .pagination {
      display: flex;
      gap: var(--space-3);
      align-items: center;
      justify-content: center;
      margin-block-start: var(--space-4);
    }
  `,
})
export class DashboardsPage {
  private readonly api = inject(DashboardsApi);
  private readonly auth = inject(AuthService);
  private readonly notify = inject(NotificationService);
  private readonly language = inject(LanguageService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<PageState>('loading');
  protected readonly error = signal<string | null>(null);
  protected readonly pipeline = signal<PipelineView | null>(null);
  protected readonly management = signal<ManagementPage | null>(null);
  protected readonly page = signal(0);
  protected readonly exporting = signal(false);

  protected readonly rows = computed(() => this.management()?.items ?? []);
  protected readonly pages = computed(() => {
    const m = this.management();
    if (!m || m.size < 1) {
      return 1;
    }
    return Math.max(1, Math.ceil(m.totalElements / m.size));
  });
  protected readonly locale = computed(() => this.language.locale());
  protected readonly pipelineTotal = computed(() => (this.pipeline()?.byUserStatus ?? []).reduce((sum, row) => sum + row.count, 0));
  /**
   * The statuses grouped into five phases of the lifecycle. `share` is the phase's percentage of the requests in
   * scope; `dash` and `offset` place its arc on a ring whose circumference is 100.
   */
  protected readonly phases = computed(() => {
    const counts = new Map((this.pipeline()?.byUserStatus ?? []).map((row) => [row.userStatus, row.count]));
    const total = Math.max(1, this.pipelineTotal());
    let start = 0;
    return PHASES.map((phase) => {
      const count = phase.statuses.reduce((sum, status) => sum + (counts.get(status) ?? 0), 0);
      const size = (count / total) * 100;
      // A one-unit gap separates neighbouring arcs, unless the arc is too small to spare it.
      const visible = Math.max(0.5, size - (size > 3 ? 1 : 0));
      const segment = { key: phase.key, count, share: Math.round(size), dash: `${visible} ${100 - visible}`, offset: -start };
      start += size;
      return segment;
    });
  });
  protected readonly lateOnPage = computed(() => this.rows().filter((row) => row.late).length);
  protected readonly pipelineMax = computed(() => Math.max(1, ...(this.pipeline()?.byUserStatus ?? []).map((row) => row.count)));
  protected readonly companyMax = computed(() => Math.max(1, ...(this.pipeline()?.byRequestingCompany ?? []).map((row) => row.count)));
  protected readonly canExport = computed(() => hasPermission(this.auth.me(), 'ERP_EXPORT'));

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    forkJoin({
      pipeline: this.api.pipeline(),
      management: this.api.management(this.page(), 20),
    })
      .pipe(
        map((data) => ({ kind: 'ready' as const, ...data })),
        catchError((err: unknown) => of({ kind: 'error' as const, problem: toApiProblem(err) })),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => {
        if (result.kind === 'error') {
          this.error.set(result.problem.code);
          this.state.set('error');
          return;
        }
        this.pipeline.set(result.pipeline);
        this.management.set(result.management);
        this.state.set('ready');
      });
  }

  /** Width of a comparison bar, as a percentage of the largest value in its group. */
  protected share(count: number, max: number): number {
    return Math.round((count / max) * 100);
  }

  protected statusTone(status: string) {
    return userStatusTone(isUserStatus(status) ? status : null);
  }

  protected go(next: number): void {
    this.page.set(Math.max(0, next));
    this.load();
  }

  protected money(amount: string | number | null | undefined, currency: string | null | undefined): string {
    if (amount == null || !currency) {
      return '—';
    }
    return formatMoney(amount, currency, minorUnitsOf(currency), this.locale());
  }

  protected exportRequests(): void {
    this.runExport(this.api.exportRequestsCsv(), 'erp-requests.csv', 'dashboards.exportedRequests');
  }

  protected exportPos(): void {
    this.runExport(this.api.exportPurchaseOrdersCsv(), 'erp-purchase-orders.csv', 'dashboards.exportedPos');
  }

  private runExport(source: ReturnType<DashboardsApi['exportRequestsCsv']>, fileName: string, toastKey: string): void {
    this.exporting.set(true);
    source.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        triggerDownload(blob, fileName);
        this.notify.success(toastKey);
        this.exporting.set(false);
      },
      error: (err: unknown) => {
        this.notify.error(`errors.${toApiProblem(err).code}`);
        this.exporting.set(false);
      },
    });
  }
}
