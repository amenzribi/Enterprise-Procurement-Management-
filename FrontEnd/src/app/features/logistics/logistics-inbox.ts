import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { LogisticsApi, LogisticsInboxPage as InboxPage } from './logistics.api';

type State = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-logistics-inbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
    DatePipe,
    EmptyState,
    ErrorState,
    PageHeader,
    ReactiveFormsModule,
    RouterLink,
    Skeleton,
    StatusBadge,
    TranslatePipe,
  ],
  template: `
    <app-page-header [heading]="'logistics.inbox.title' | translate" [description]="'logistics.inbox.description' | translate" />

    <div class="toolbar" role="search">
      <label class="visually-hidden" for="logistics-q">{{ 'logistics.inbox.search' | translate }}</label>
      <input
        id="logistics-q"
        type="search"
        [formControl]="search"
        [attr.placeholder]="'logistics.inbox.searchPlaceholder' | translate"
      />
      <button appButton type="button" variant="secondary" (click)="reset()">{{ 'common.reset' | translate }}</button>
    </div>

    @switch (state()) {
      @case ('loading') {
        <app-skeleton height="10rem" />
      }
      @case ('error') {
        <app-error-state
          [heading]="'logistics.inbox.loadError' | translate"
          [retryLabel]="'common.retry' | translate"
          (retry)="load()"
        />
      }
      @default {
        @if (page()?.items?.length === 0) {
          <app-empty-state
            icon="truck"
            [heading]="(q() ? 'logistics.inbox.noMatch.title' : 'logistics.inbox.empty.title') | translate"
            [message]="(q() ? 'logistics.inbox.noMatch.message' : 'logistics.inbox.empty.message') | translate"
          />
        } @else {
          <ul class="list">
            @for (item of page()?.items ?? []; track item.poId) {
              <li>
                <a class="card" [routerLink]="['/requests', item.requestId]">
                  <div class="refs">
                    <span
                      ><span class="label">{{ 'logistics.inbox.requestRef' | translate }}</span>
                      <bdi class="tabular">{{ item.requestNumber || '—' }}</bdi></span
                    >
                    <span
                      ><span class="label">{{ 'logistics.inbox.poNumber' | translate }}</span>
                      <bdi class="tabular">{{ item.poNumber }}</bdi></span
                    >
                    @if (item.shipmentNumber) {
                      <span
                        ><span class="label">{{ 'logistics.inbox.shipmentRef' | translate }}</span>
                        <bdi class="tabular">{{ item.shipmentNumber }}</bdi></span
                      >
                    }
                  </div>
                  <p class="title">{{ item.requestTitle }}</p>
                  <div class="meta">
                    <app-status-badge [tone]="item.readyForReceipt ? 'success' : 'info'">
                      {{
                        (item.readyForReceipt
                          ? 'logistics.inbox.readyForReceipt'
                          : item.shipmentStatus
                            ? 'logistics.status.' + item.shipmentStatus
                            : 'logistics.inbox.needsShipment') | translate
                      }}
                    </app-status-badge>
                    @if (item.poCreatedAt) {
                      <time [attr.datetime]="item.poCreatedAt">{{ item.poCreatedAt | date: 'medium' }}</time>
                    }
                  </div>
                </a>
              </li>
            }
          </ul>
          <div class="pager">
            <button
              appButton
              type="button"
              variant="secondary"
              [disabled]="page()?.page === 0"
              (click)="go(page()!.page - 1)"
            >
              {{ 'common.previous' | translate }}
            </button>
            <span class="tabular">{{ (page()?.page ?? 0) + 1 }} / {{ page()?.totalPages || 1 }}</span>
            <button
              appButton
              type="button"
              variant="secondary"
              [disabled]="((page()?.page ?? 0) + 1) >= (page()?.totalPages || 1)"
              (click)="go(page()!.page + 1)"
            >
              {{ 'common.next' | translate }}
            </button>
          </div>
        }
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin-block-end: var(--space-4);
    }
    input[type='search'] {
      flex: 1 1 16rem;
      min-block-size: 2.5rem;
      padding-inline: var(--space-3);
      border: 1px solid var(--color-line-strong);
      border-radius: var(--radius-sm);
    }
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: var(--space-3);
    }
    .card {
      display: grid;
      gap: var(--space-2);
      padding: var(--space-4);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-sm);
      text-decoration: none;
      color: inherit;
    }
    .card:focus-visible {
      outline: 2px solid var(--color-focus, var(--color-accent));
      outline-offset: 2px;
    }
    .refs {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-3);
      font-size: var(--text-sm);
    }
    .label {
      display: block;
      color: var(--color-text-muted);
    }
    .tabular {
      font-weight: var(--weight-semibold);
      user-select: all;
      overflow-wrap: anywhere;
    }
    .title {
      margin: 0;
      overflow-wrap: anywhere;
    }
    .meta {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      align-items: center;
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }
    .pager {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-3);
      align-items: center;
      margin-block-start: var(--space-4);
    }
  `,
})
export class LogisticsInboxComponent {
  private readonly api = inject(LogisticsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly state = signal<State>('loading');
  protected readonly page = signal<InboxPage | null>(null);
  protected readonly q = signal('');
  protected readonly search = new FormControl('', { nonNullable: true });

  constructor() {
    const initial = this.route.snapshot.queryParamMap.get('q') ?? '';
    this.search.setValue(initial, { emitEvent: false });
    this.q.set(initial.trim());
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q.set(params.get('q')?.trim() ?? '');
      this.load(Number(params.get('page')) || 0);
    });
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        void this.router.navigate([], {
          relativeTo: this.route,
          queryParams: { q: value.trim() || null, page: null },
          queryParamsHandling: 'merge',
        });
      });
  }

  protected load(page = this.page()?.page ?? 0): void {
    this.state.set('loading');
    this.api.inbox(this.q() || undefined, page, 20).subscribe({
      next: (result) => {
        this.page.set(result);
        this.state.set('ready');
      },
      error: (error: unknown) => {
        if (toApiProblem(error).status !== 401) {
          this.state.set('error');
        }
      },
    });
  }

  protected reset(): void {
    this.search.setValue('');
    void this.router.navigate([], { relativeTo: this.route, queryParams: {} });
  }

  protected go(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { page },
      queryParamsHandling: 'merge',
    });
  }
}
