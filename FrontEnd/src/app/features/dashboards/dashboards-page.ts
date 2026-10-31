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
import { DashboardsApi } from './dashboards.api';
import { ManagementPage, PipelineView } from './dashboards.models';

type PageState = 'loading' | 'ready' | 'error';

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
              <ul class="counts" role="list">
                @for (row of pipe.byUserStatus; track row.userStatus) {
                  <li>
                    <span>{{ 'requests.userStatus.' + row.userStatus | translate }}</span>
                    <strong class="tabular">{{ row.count }}</strong>
                  </li>
                }
              </ul>
              @if (pipe.openAmountsByCurrency.length) {
                <p class="muted">{{ 'dashboards.pipeline.amountsNote' | translate }}</p>
                <ul class="amounts" role="list">
                  @for (row of pipe.openAmountsByCurrency; track row.currency) {
                    <li>
                      <span>{{ row.currency }}</span>
                      <strong class="tabular">{{ money(row.amount, row.currency) }}</strong>
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
                <ul class="counts" role="list">
                  @for (row of pipe.byRequestingCompany; track row.companyCode) {
                    <li>
                      <span>{{ row.companyName }} <span class="muted">({{ row.companyCode }})</span></span>
                      <strong class="tabular">{{ row.count }}</strong>
                    </li>
                  }
                </ul>
              }
            </section>
          }
        }

        <section class="panel" aria-labelledby="mgmt-title">
          <h2 class="panel-title" id="mgmt-title">{{ 'dashboards.management.title' | translate }}</h2>
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
                      <td>
                        <a [routerLink]="['/requests', row.id]">{{ row.number ?? ('requests.list.draftNumber' | translate) }}</a>
                      </td>
                      <td>{{ row.title }}</td>
                      <td>
                        @if (row.userStatus) {
                          {{ 'requests.userStatus.' + row.userStatus | translate }}
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
      background: var(--color-surface);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-md);
      padding: var(--space-4);
      margin-block-end: var(--space-4);
    }

    .panel-title {
      margin: 0 0 var(--space-3);
      font-size: var(--font-size-lg);
    }

    .counts,
    .amounts {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--space-2);
    }

    .counts li,
    .amounts li {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: baseline;
      justify-content: space-between;
      padding-block: var(--space-1);
      border-block-end: 1px solid var(--color-line);
    }

    .muted {
      color: var(--color-text-muted);
    }

    .small {
      font-size: var(--font-size-sm);
      display: block;
    }

    .table-wrap {
      overflow-x: auto;
    }

    .table {
      width: 100%;
      border-collapse: collapse;
    }

    .table th,
    .table td {
      text-align: start;
      padding: var(--space-2);
      border-block-end: 1px solid var(--color-line);
      vertical-align: top;
    }

    .numeric {
      text-align: end;
    }

    .tabular {
      font-variant-numeric: tabular-nums;
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
