import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';

const API = '/api/v1/admin/requests';

interface ActionAvailability {
  action: string;
  allowed: boolean;
  reasonCode: string;
  explanation: string;
}

interface LinkedCounts {
  approvals: number;
  rfqs: number;
  quotations: number;
  awards: number;
  purchaseOrders: number;
  shipments: number;
  receipts: number;
  invoices: number;
}

interface Ref {
  id: number;
  code: string;
  name: string;
}

interface UserRef {
  id: number;
  displayName: string;
}

interface Summary {
  id: number;
  number: string | null;
  title: string;
  status: string;
  userStatus: string;
  stage: string;
  archived: boolean;
  totalAmount: number | null;
  currency: string | null;
  updatedAt: string;
  company: Ref;
  requester: UserRef;
  allowedActions: string[];
  actionMatrix: ActionAvailability[];
  links: LinkedCounts;
}

interface Detail {
  id: number;
  number: string | null;
  title: string;
  status: string;
  userStatus: string;
  stage: string;
  version: number;
  archived: boolean;
  archivedAt?: string | null;
  archiveReason?: string | null;
  totalAmount: number | null;
  currency: string | null;
  justification?: string | null;
  createdAt: string;
  updatedAt: string;
  submittedAt?: string | null;
  company: Ref;
  requester: UserRef;
  enteredBy: UserRef;
  currentOwner?: UserRef | null;
  actions: ActionAvailability[];
  linkCounts: LinkedCounts;
  linkedItems: { type: string; id: number; reference: string; status: string }[];
  nextStepSummary: string;
  workflowNote: string;
}

interface CleanupPreview {
  previewToken: string;
  expiresAt: string;
  candidateCount: number;
  deletableCount: number;
  cancellableCount: number;
  archivableCount: number;
  blockedCount: number;
  candidates: {
    id: number;
    number: string | null;
    title: string;
    status: string;
    archived: boolean;
    company: Ref;
    links: LinkedCounts;
    recommendedAction: string | null;
    blockReason: string | null;
  }[];
  impactSummary: string;
}

@Component({
  selector: 'app-request-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
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
    <app-page-header
      [heading]="'requestAdmin.title' | translate"
      [description]="'requestAdmin.description' | translate"
    />

    <p class="hint">{{ 'requestAdmin.boundary' | translate }}</p>

    <form class="filters" [formGroup]="filters" (ngSubmit)="load()">
      <label>
        <span>{{ 'requestAdmin.search' | translate }}</span>
        <input type="search" formControlName="q" />
      </label>
      <label>
        <span>{{ 'requestAdmin.status' | translate }}</span>
        <input type="text" formControlName="status" placeholder="DRAFT, CLOSED…" />
      </label>
      <label>
        <span>{{ 'requestAdmin.archivedFilter' | translate }}</span>
        <select formControlName="archived">
          <option value="">{{ 'requestAdmin.archivedAll' | translate }}</option>
          <option value="false">{{ 'requestAdmin.archivedActive' | translate }}</option>
          <option value="true">{{ 'requestAdmin.archivedOnly' | translate }}</option>
        </select>
      </label>
      <button appButton type="submit">{{ 'requestAdmin.apply' | translate }}</button>
      <button appButton type="button" variant="secondary" (click)="previewCleanup()">
        {{ 'requestAdmin.cleanupPreview' | translate }}
      </button>
    </form>

    @if (state() === 'loading') {
      <app-skeleton height="10rem" />
    } @else if (state() === 'error') {
      <app-error-state
        [heading]="'requestAdmin.loadError' | translate"
        [message]="error()"
        (retry)="load()"
      />
    } @else if (items().length === 0) {
      <app-empty-state
        [heading]="'requestAdmin.empty.title' | translate"
        [message]="'requestAdmin.empty.message' | translate"
      />
    } @else {
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>{{ 'requestAdmin.cols.reference' | translate }}</th>
              <th>{{ 'requestAdmin.cols.title' | translate }}</th>
              <th>{{ 'requestAdmin.cols.company' | translate }}</th>
              <th>{{ 'requestAdmin.cols.requester' | translate }}</th>
              <th>{{ 'requestAdmin.cols.status' | translate }}</th>
              <th>{{ 'requestAdmin.cols.actions' | translate }}</th>
            </tr>
          </thead>
          <tbody>
            @for (row of items(); track row.id) {
              <tr>
                <td>
                  <a [routerLink]="['/admin/requests', row.id]">{{ row.number || ('#' + row.id) }}</a>
                  @if (row.archived) {
                    <app-status-badge tone="neutral">{{ 'requestAdmin.archived' | translate }}</app-status-badge>
                  }
                </td>
                <td>{{ row.title }}</td>
                <td>{{ row.company.code }} — {{ row.company.name }}</td>
                <td>{{ row.requester.displayName }}</td>
                <td>{{ row.status }}</td>
                <td>{{ row.allowedActions.join(', ') || '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (preview(); as p) {
      <section class="preview" aria-labelledby="cleanup-preview-heading">
        <h2 id="cleanup-preview-heading">{{ 'requestAdmin.cleanup.title' | translate }}</h2>
        <p>{{ p.impactSummary }}</p>
        <p>
          {{
            'requestAdmin.cleanup.summary'
              | translate
                : {
                    total: p.candidateCount,
                    deletable: p.deletableCount,
                    cancellable: p.cancellableCount,
                    archivable: p.archivableCount,
                    blocked: p.blockedCount,
                  }
          }}
        </p>
        <ul>
          @for (c of p.candidates; track c.id) {
            <li>
              <label>
                <input
                  type="checkbox"
                  [checked]="selected().has(c.id)"
                  [disabled]="!c.recommendedAction"
                  (change)="toggleSelect(c.id, $event)"
                />
                <strong>{{ c.number || ('#' + c.id) }}</strong> — {{ c.title }} ({{ c.status }})
                · {{ c.recommendedAction || c.blockReason }}
              </label>
            </li>
          }
        </ul>
        <label>
          <span>{{ 'requestAdmin.reason' | translate }}</span>
          <textarea [formControl]="cleanupReason" rows="2"></textarea>
        </label>
        <div class="row">
          <button
            appButton
            type="button"
            [disabled]="selected().size === 0 || cleanupBusy()"
            (click)="executeCleanup()"
          >
            {{ 'requestAdmin.cleanup.confirm' | translate }}
          </button>
          <button appButton type="button" variant="secondary" (click)="preview.set(null)">
            {{ 'requestAdmin.cleanup.dismiss' | translate }}
          </button>
        </div>
        <p class="warn">{{ 'requestAdmin.cleanup.warn' | translate }}</p>
      </section>
    }
  `,
  styles: `
    .hint,
    .warn {
      max-inline-size: 60rem;
      color: var(--color-text-muted, #5c6570);
      margin-block: 0.5rem 1rem;
    }
    .warn {
      color: var(--color-danger, #9b1c1c);
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: end;
      margin-block-end: 1rem;
    }
    .filters label {
      display: grid;
      gap: 0.25rem;
      font-size: 0.875rem;
    }
    .filters input,
    .filters select,
    textarea {
      min-inline-size: 12rem;
      padding: 0.4rem 0.55rem;
    }
    .table-wrap {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.9rem;
    }
    th,
    td {
      text-align: start;
      padding: 0.55rem 0.65rem;
      border-block-end: 1px solid var(--color-border, #d8dee6);
      vertical-align: top;
    }
    .preview {
      margin-block-start: 1.5rem;
      padding: 1rem;
      border: 1px solid var(--color-border, #d8dee6);
      border-radius: 0.5rem;
    }
    .preview ul {
      list-style: none;
      padding: 0;
      display: grid;
      gap: 0.35rem;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-block: 0.75rem;
    }
  `,
})
export class RequestAdminPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(NotificationService);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly error = signal('');
  protected readonly items = signal<Summary[]>([]);
  protected readonly preview = signal<CleanupPreview | null>(null);
  protected readonly selected = signal(new Set<number>());
  protected readonly cleanupBusy = signal(false);
  protected readonly cleanupReason = new FormControl('', { nonNullable: true });

  protected readonly filters = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    status: new FormControl('', { nonNullable: true }),
    archived: new FormControl('', { nonNullable: true }),
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    let params = new HttpParams().set('page', '0').set('size', '50');
    const v = this.filters.getRawValue();
    if (v.q.trim()) {
      params = params.set('q', v.q.trim());
    }
    if (v.status.trim()) {
      params = params.set('status', v.status.trim());
    }
    if (v.archived === 'true' || v.archived === 'false') {
      params = params.set('archived', v.archived);
    }
    this.http.get<{ items: Summary[] }>(API, { params }).subscribe({
      next: (page) => {
        this.items.set(page.items ?? []);
        this.state.set('ready');
      },
      error: (err) => {
        this.error.set(`errors.${toApiProblem(err).code}`);
        this.state.set('error');
      },
    });
  }

  previewCleanup(): void {
    const v = this.filters.getRawValue();
    const body = {
      q: v.q.trim() || null,
      status: v.status.trim() || null,
      archived: v.archived === '' ? null : v.archived === 'true',
      draftsOnly: v.status.trim().toUpperCase() === 'DRAFT' ? true : null,
    };
    this.http.post<CleanupPreview>(`${API}/cleanup/preview`, body).subscribe({
      next: (p) => {
        this.preview.set(p);
        this.selected.set(new Set(p.candidates.filter((c) => c.recommendedAction).map((c) => c.id)));
        this.notifications.info('requestAdmin.cleanup.ready');
      },
      error: (err) => this.notifications.error(`errors.${toApiProblem(err).code}`),
    });
  }

  toggleSelect(id: number, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const next = new Set(this.selected());
    if (checked) {
      next.add(id);
    } else {
      next.delete(id);
    }
    this.selected.set(next);
  }

  executeCleanup(): void {
    const p = this.preview();
    if (!p) {
      return;
    }
    const reason = this.cleanupReason.value.trim();
    if (reason.length < 3) {
      this.notifications.error('requestAdmin.reasonRequired');
      return;
    }
    this.cleanupBusy.set(true);
    this.http
      .post(`${API}/cleanup/execute`, {
        previewToken: p.previewToken,
        ids: [...this.selected()],
        confirmation: 'APPLY',
        reason,
      })
      .subscribe({
        next: () => {
          this.cleanupBusy.set(false);
          this.preview.set(null);
          this.selected.set(new Set());
          this.notifications.success('requestAdmin.cleanup.done');
          this.load();
        },
        error: (err) => {
          this.cleanupBusy.set(false);
          this.notifications.error(`errors.${toApiProblem(err).code}`);
        },
      });
  }
}

@Component({
  selector: 'app-request-admin-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    Button,
    ErrorState,
    PageHeader,
    ReactiveFormsModule,
    RouterLink,
    Skeleton,
    StatusBadge,
    TranslatePipe,
  ],
  template: `
    <app-page-header
      [heading]="'requestAdmin.detailTitle' | translate"
      [description]="'requestAdmin.detailDescription' | translate"
    />
    <p><a routerLink="/admin/requests">{{ 'requestAdmin.back' | translate }}</a></p>

    @if (state() === 'loading') {
      <app-skeleton height="12rem" />
    } @else if (state() === 'error') {
      <app-error-state
        [heading]="'requestAdmin.loadError' | translate"
        [message]="error()"
        (retry)="load()"
      />
    } @else if (detail(); as d) {
      <section class="card">
        <header>
          <h2>{{ d.number || ('#' + d.id) }} — {{ d.title }}</h2>
          <app-status-badge [tone]="d.archived ? 'neutral' : 'info'">{{ d.status }}</app-status-badge>
          @if (d.archived) {
            <app-status-badge tone="neutral">{{ 'requestAdmin.archived' | translate }}</app-status-badge>
          }
        </header>
        <p><strong>{{ 'requestAdmin.cols.company' | translate }}:</strong> {{ d.company.code }} — {{ d.company.name }}</p>
        <p><strong>{{ 'requestAdmin.cols.requester' | translate }}:</strong> {{ d.requester.displayName }}</p>
        <p><strong>{{ 'requestAdmin.nextStep' | translate }}:</strong> {{ d.nextStepSummary }}</p>
        <p>{{ d.workflowNote }}</p>

        <h3>{{ 'requestAdmin.links' | translate }}</h3>
        <ul class="counts">
          <li>Approvals: {{ d.linkCounts.approvals }}</li>
          <li>RFQs: {{ d.linkCounts.rfqs }}</li>
          <li>Quotations: {{ d.linkCounts.quotations }}</li>
          <li>Awards: {{ d.linkCounts.awards }}</li>
          <li>POs: {{ d.linkCounts.purchaseOrders }}</li>
          <li>Shipments: {{ d.linkCounts.shipments }}</li>
          <li>Receipts: {{ d.linkCounts.receipts }}</li>
          <li>Invoices: {{ d.linkCounts.invoices }}</li>
        </ul>
        @if (d.linkedItems.length) {
          <ul>
            @for (item of d.linkedItems; track item.type + item.id) {
              <li>{{ item.type }} {{ item.reference }} ({{ item.status }})</li>
            }
          </ul>
        }

        <h3>{{ 'requestAdmin.actionMatrix' | translate }}</h3>
        <ul class="matrix">
          @for (a of d.actions; track a.action) {
            <li [class.denied]="!a.allowed">
              <strong>{{ a.action }}</strong>
              — {{ a.allowed ? ('requestAdmin.allowed' | translate) : ('requestAdmin.blocked' | translate) }}:
              {{ a.explanation }}
            </li>
          }
        </ul>

        <label>
          <span>{{ 'requestAdmin.reason' | translate }}</span>
          <textarea [formControl]="reason" rows="2"></textarea>
        </label>

        <div class="row">
          @for (a of d.actions; track a.action) {
            @if (a.allowed && actionable(a.action)) {
              <button appButton type="button" [disabled]="busy()" (click)="run(a.action)">
                {{ ('requestAdmin.act.' + a.action) | translate }}
              </button>
            }
          }
          <a appButton variant="secondary" [routerLink]="['/requests', d.id]">
            {{ 'requestAdmin.openDossier' | translate }}
          </a>
        </div>
      </section>
    }
  `,
  styles: `
    .card {
      display: grid;
      gap: 0.75rem;
      max-inline-size: 56rem;
    }
    .counts,
    .matrix {
      display: grid;
      gap: 0.35rem;
      padding-inline-start: 1.1rem;
    }
    .matrix .denied {
      color: var(--color-text-muted, #5c6570);
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    textarea {
      width: 100%;
      max-inline-size: 40rem;
      padding: 0.4rem 0.55rem;
    }
  `,
})
export class RequestAdminDetailPage implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly error = signal('');
  protected readonly detail = signal<Detail | null>(null);
  protected readonly busy = signal(false);
  protected readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.minLength(3)],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.state.set('loading');
    this.http.get<Detail>(`${API}/${id}`).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.state.set('ready');
      },
      error: (err) => {
        this.error.set(`errors.${toApiProblem(err).code}`);
        this.state.set('error');
      },
    });
  }

  actionable(action: string): boolean {
    return ['ARCHIVE', 'RESTORE', 'CANCEL', 'DELETE_DRAFT', 'RETURN_FOR_REVISION'].includes(action);
  }

  run(action: string): void {
    const d = this.detail();
    if (!d) {
      return;
    }
    const reason = this.reason.value.trim();
    if (reason.length < 3) {
      this.notifications.error('requestAdmin.reasonRequired');
      return;
    }
    this.busy.set(true);
    const body = { version: d.version, reason };
    const done = () => {
      this.busy.set(false);
      this.load();
    };
    const fail = (err: unknown) => {
      this.busy.set(false);
      this.notifications.error(`errors.${toApiProblem(err).code}`);
    };
    switch (action) {
      case 'ARCHIVE':
        this.http.post(`${API}/${d.id}/archive`, body).subscribe({ next: done, error: fail });
        break;
      case 'RESTORE':
        this.http.post(`${API}/${d.id}/restore`, body).subscribe({ next: done, error: fail });
        break;
      case 'CANCEL':
        this.http.post(`${API}/${d.id}/cancel`, body).subscribe({ next: done, error: fail });
        break;
      case 'RETURN_FOR_REVISION':
        this.http.post(`${API}/${d.id}/return-for-revision`, body).subscribe({ next: done, error: fail });
        break;
      case 'DELETE_DRAFT':
        this.http
          .delete(`${API}/${d.id}`, { params: { version: d.version, reason } })
          .subscribe({
            next: () => {
              this.busy.set(false);
              this.notifications.success('requestAdmin.deleted');
              void this.router.navigateByUrl('/admin/requests');
            },
            error: fail,
          });
        break;
      default:
        this.busy.set(false);
    }
  }
}
