import { HttpClient, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
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

interface LineView {
  id: number | null;
  lineNo: number;
  description: string | null;
  itemReference: string | null;
  quantity: number | null;
  unit: string | null;
  estimatedUnitPrice: number | null;
  lineTotal: number | null;
  requiredDate: string | null;
  deliveryLocation: string | null;
}

interface Summary {
  id: number;
  number: string | null;
  title: string;
  status: string;
  userStatus: string;
  stage: string;
  archived: boolean;
  removed: boolean;
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
  removed: boolean;
  archivedAt?: string | null;
  archiveReason?: string | null;
  removedAt?: string | null;
  removeReason?: string | null;
  totalAmount: number | null;
  currency: string | null;
  justification?: string | null;
  categoryId?: number | null;
  priority?: string | null;
  purchaseType?: string | null;
  departmentId?: number | null;
  siteId?: number | null;
  costCenterId?: number | null;
  requiredDate?: string | null;
  createdAt: string;
  updatedAt: string;
  submittedAt?: string | null;
  company: Ref;
  requester: UserRef;
  enteredBy: UserRef;
  currentOwner?: UserRef | null;
  lines: LineView[];
  actions: ActionAvailability[];
  linkCounts: LinkedCounts;
  linkedItems: { type: string; id: number; reference: string; status: string }[];
  nextStepSummary: string;
  workflowNote: string;
  administrativeStatus: string;
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
      <label>
        <span>{{ 'requestAdmin.removedFilter' | translate }}</span>
        <select formControlName="removed">
          <option value="">{{ 'requestAdmin.removedAll' | translate }}</option>
          <option value="false">{{ 'requestAdmin.removedActive' | translate }}</option>
          <option value="true">{{ 'requestAdmin.removedOnly' | translate }}</option>
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
                  @if (row.removed) {
                    <app-status-badge tone="neutral">{{ 'requestAdmin.removed' | translate }}</app-status-badge>
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
    removed: new FormControl('', { nonNullable: true }),
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
    if (v.removed === 'true' || v.removed === 'false') {
      params = params.set('removed', v.removed);
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
      removed: v.removed === '' ? null : v.removed === 'true',
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
          <app-status-badge [tone]="d.archived || d.removed ? 'neutral' : 'info'">{{ d.status }}</app-status-badge>
          @if (d.archived) {
            <app-status-badge tone="neutral">{{ 'requestAdmin.archived' | translate }}</app-status-badge>
          }
          @if (d.removed) {
            <app-status-badge tone="neutral">{{ 'requestAdmin.removed' | translate }}</app-status-badge>
          }
          <app-status-badge tone="neutral">{{
            ('requestAdmin.adminStatus.' + d.administrativeStatus) | translate
          }}</app-status-badge>
        </header>
        <p><strong>{{ 'requestAdmin.cols.company' | translate }}:</strong> {{ d.company.code }} — {{ d.company.name }}</p>
        <p><strong>{{ 'requestAdmin.cols.requester' | translate }}:</strong> {{ d.requester.displayName }}</p>
        <p>
          <strong>{{ 'requestAdmin.cols.owner' | translate }}:</strong>
          {{ d.currentOwner?.displayName || ('requestAdmin.noOwner' | translate) }}
        </p>
        <p><strong>{{ 'requestAdmin.businessStatus' | translate }}:</strong> {{ d.userStatus }} ({{ d.status }})</p>
        <p><strong>{{ 'requestAdmin.nextStep' | translate }}:</strong> {{ d.nextStepSummary }}</p>
        <p>{{ d.workflowNote }}</p>

        <h3>{{ 'requestAdmin.links' | translate }}</h3>
        <ul class="counts">
          <li>{{ 'requestAdmin.link.approvals' | translate }}: {{ d.linkCounts.approvals }}</li>
          <li>{{ 'requestAdmin.link.rfqs' | translate }}: {{ d.linkCounts.rfqs }}</li>
          <li>{{ 'requestAdmin.link.quotations' | translate }}: {{ d.linkCounts.quotations }}</li>
          <li>{{ 'requestAdmin.link.awards' | translate }}: {{ d.linkCounts.awards }}</li>
          <li>{{ 'requestAdmin.link.pos' | translate }}: {{ d.linkCounts.purchaseOrders }}</li>
          <li>{{ 'requestAdmin.link.shipments' | translate }}: {{ d.linkCounts.shipments }}</li>
          <li>{{ 'requestAdmin.link.receipts' | translate }}: {{ d.linkCounts.receipts }}</li>
          <li>{{ 'requestAdmin.link.invoices' | translate }}: {{ d.linkCounts.invoices }}</li>
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
              <strong>{{ ('requestAdmin.act.' + a.action) | translate }}</strong>
              — {{ a.allowed ? ('requestAdmin.allowed' | translate) : ('requestAdmin.blocked' | translate) }}:
              {{ a.explanation }}
            </li>
          }
        </ul>

        @if (showCorrectionForm()) {
          <section class="correction" [formGroup]="correction" aria-labelledby="correction-heading">
            <h3 id="correction-heading">{{ correctionHeadingKey() | translate }}</h3>
            <p class="hint">{{ correctionHintKey() | translate }}</p>
            <label>
              <span>{{ 'requestAdmin.form.title' | translate }}</span>
              <input type="text" formControlName="title" />
            </label>
            <label>
              <span>{{ 'requestAdmin.form.justification' | translate }}</span>
              <textarea formControlName="justification" rows="3"></textarea>
            </label>
            <div class="grid">
              <label>
                <span>{{ 'requestAdmin.form.priority' | translate }}</span>
                <select formControlName="priority">
                  <option value="">—</option>
                  <option value="LOW">LOW</option>
                  <option value="NORMAL">NORMAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="URGENT">URGENT</option>
                </select>
              </label>
              <label>
                <span>{{ 'requestAdmin.form.purchaseType' | translate }}</span>
                <select formControlName="purchaseType">
                  <option value="">—</option>
                  <option value="LOCAL">LOCAL</option>
                  <option value="INTERNATIONAL">INTERNATIONAL</option>
                </select>
              </label>
              <label>
                <span>{{ 'requestAdmin.form.currency' | translate }}</span>
                <input type="text" formControlName="currency" />
              </label>
              <label>
                <span>{{ 'requestAdmin.form.requiredDate' | translate }}</span>
                <input type="date" formControlName="requiredDate" />
              </label>
              <label>
                <span>{{ 'requestAdmin.form.categoryId' | translate }}</span>
                <input type="number" formControlName="categoryId" />
              </label>
              <label>
                <span>{{ 'requestAdmin.form.departmentId' | translate }}</span>
                <input type="number" formControlName="departmentId" />
              </label>
              <label>
                <span>{{ 'requestAdmin.form.siteId' | translate }}</span>
                <input type="number" formControlName="siteId" />
              </label>
              <label>
                <span>{{ 'requestAdmin.form.costCenterId' | translate }}</span>
                <input type="number" formControlName="costCenterId" />
              </label>
            </div>
            <h4>{{ 'requestAdmin.form.lines' | translate }}</h4>
            <div formArrayName="lines" class="lines">
              @for (line of lines.controls; track $index; let i = $index) {
                <div class="line" [formGroupName]="i">
                  <label>
                    <span>{{ 'requestAdmin.form.description' | translate }}</span>
                    <input type="text" formControlName="description" />
                  </label>
                  <label>
                    <span>{{ 'requestAdmin.form.quantity' | translate }}</span>
                    <input type="number" formControlName="quantity" />
                  </label>
                  <label>
                    <span>{{ 'requestAdmin.form.unit' | translate }}</span>
                    <input type="text" formControlName="unit" />
                  </label>
                  <label>
                    <span>{{ 'requestAdmin.form.unitPrice' | translate }}</span>
                    <input type="number" formControlName="estimatedUnitPrice" />
                  </label>
                </div>
              }
            </div>
            <button appButton type="button" variant="secondary" (click)="addLine()">
              {{ 'requestAdmin.form.addLine' | translate }}
            </button>
          </section>
        }

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
    textarea,
    input,
    select {
      width: 100%;
      max-inline-size: 40rem;
      padding: 0.4rem 0.55rem;
    }
    .correction {
      display: grid;
      gap: 0.65rem;
      padding: 0.85rem;
      border: 1px solid var(--color-border, #d8dee6);
      border-radius: 0.5rem;
    }
    .correction .hint {
      margin: 0;
      color: var(--color-text-muted, #5c6570);
    }
    .grid {
      display: grid;
      gap: 0.65rem;
      grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
    }
    .lines {
      display: grid;
      gap: 0.75rem;
    }
    .line {
      display: grid;
      gap: 0.5rem;
      grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr));
      padding: 0.5rem;
      background: var(--color-surface-muted, #f5f7fa);
      border-radius: 0.35rem;
    }
    label {
      display: grid;
      gap: 0.25rem;
      font-size: 0.875rem;
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
  protected readonly correction = new FormGroup({
    title: new FormControl('', { nonNullable: true }),
    justification: new FormControl('', { nonNullable: true }),
    priority: new FormControl('', { nonNullable: true }),
    purchaseType: new FormControl('', { nonNullable: true }),
    currency: new FormControl('', { nonNullable: true }),
    requiredDate: new FormControl('', { nonNullable: true }),
    categoryId: new FormControl<number | null>(null),
    departmentId: new FormControl<number | null>(null),
    siteId: new FormControl<number | null>(null),
    costCenterId: new FormControl<number | null>(null),
    lines: new FormArray<FormGroup>([]),
  });

  protected get lines(): FormArray<FormGroup> {
    return this.correction.get('lines') as FormArray<FormGroup>;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.state.set('loading');
    this.http.get<Detail>(`${API}/${id}`).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.patchCorrection(d);
        this.state.set('ready');
      },
      error: (err) => {
        this.error.set(`errors.${toApiProblem(err).code}`);
        this.state.set('error');
      },
    });
  }

  showCorrectionForm(): boolean {
    const d = this.detail();
    if (!d) {
      return false;
    }
    return d.actions.some(
      (a) => a.allowed && (a.action === 'CORRECT_DRAFT' || a.action === 'CORRECT_SUBMITTED'),
    );
  }

  correctionHeadingKey(): string {
    const d = this.detail();
    const submitted = d?.actions.some((a) => a.allowed && a.action === 'CORRECT_SUBMITTED');
    return submitted ? 'requestAdmin.form.submittedHeading' : 'requestAdmin.form.draftHeading';
  }

  correctionHintKey(): string {
    const d = this.detail();
    const submitted = d?.actions.some((a) => a.allowed && a.action === 'CORRECT_SUBMITTED');
    return submitted ? 'requestAdmin.form.submittedHint' : 'requestAdmin.form.draftHint';
  }

  actionable(action: string): boolean {
    return [
      'ARCHIVE',
      'RESTORE',
      'CANCEL',
      'DELETE_DRAFT',
      'RETURN_FOR_REVISION',
      'CORRECT_DRAFT',
      'CORRECT_SUBMITTED',
      'REMOVE',
      'RESTORE_REMOVED',
    ].includes(action);
  }

  addLine(): void {
    this.lines.push(this.lineGroup());
  }

  private lineGroup(line?: LineView): FormGroup {
    return new FormGroup({
      id: new FormControl<number | null>(line?.id ?? null),
      description: new FormControl(line?.description ?? '', { nonNullable: true }),
      itemReference: new FormControl(line?.itemReference ?? '', { nonNullable: true }),
      quantity: new FormControl<number | null>(line?.quantity ?? null),
      unit: new FormControl(line?.unit ?? '', { nonNullable: true }),
      estimatedUnitPrice: new FormControl<number | null>(line?.estimatedUnitPrice ?? null),
      requiredDate: new FormControl(line?.requiredDate ?? '', { nonNullable: true }),
      deliveryLocation: new FormControl(line?.deliveryLocation ?? '', { nonNullable: true }),
    });
  }

  private patchCorrection(d: Detail): void {
    this.correction.patchValue({
      title: d.title ?? '',
      justification: d.justification ?? '',
      priority: d.priority ?? '',
      purchaseType: d.purchaseType ?? '',
      currency: d.currency ?? '',
      requiredDate: d.requiredDate ?? '',
      categoryId: d.categoryId ?? null,
      departmentId: d.departmentId ?? null,
      siteId: d.siteId ?? null,
      costCenterId: d.costCenterId ?? null,
    });
    this.lines.clear();
    for (const line of d.lines ?? []) {
      this.lines.push(this.lineGroup(line));
    }
    if (this.lines.length === 0) {
      this.lines.push(this.lineGroup());
    }
  }

  private correctionBody(d: Detail, reason: string): Record<string, unknown> {
    const v = this.correction.getRawValue();
    return {
      version: d.version,
      reason,
      title: v.title,
      justification: v.justification,
      priority: v.priority || null,
      purchaseType: v.purchaseType || null,
      currency: v.currency || null,
      requiredDate: v.requiredDate || null,
      categoryId: v.categoryId,
      departmentId: v.departmentId,
      siteId: v.siteId,
      costCenterId: v.costCenterId,
      lines: (v.lines as Record<string, unknown>[]).map((line) => ({
        id: line['id'],
        description: line['description'],
        itemReference: line['itemReference'] || null,
        quantity: line['quantity'],
        unit: line['unit'] || null,
        estimatedUnitPrice: line['estimatedUnitPrice'],
        requiredDate: line['requiredDate'] || null,
        deliveryLocation: line['deliveryLocation'] || null,
      })),
    };
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
    if (action === 'CORRECT_SUBMITTED') {
      const confirmed = window.confirm(
        'This invalidates pending approvals and unused one-tap links, then starts a new approval round. Continue?',
      );
      if (!confirmed) {
        return;
      }
    }
    this.busy.set(true);
    const body = { version: d.version, reason };
    const done = () => {
      this.busy.set(false);
      this.notifications.success('requestAdmin.actionDone');
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
      case 'REMOVE':
        this.http.post(`${API}/${d.id}/remove`, body).subscribe({ next: done, error: fail });
        break;
      case 'RESTORE_REMOVED':
        this.http.post(`${API}/${d.id}/restore-removed`, body).subscribe({ next: done, error: fail });
        break;
      case 'CORRECT_DRAFT':
        this.http
          .post(`${API}/${d.id}/correct-draft`, this.correctionBody(d, reason))
          .subscribe({ next: done, error: fail });
        break;
      case 'CORRECT_SUBMITTED':
        this.http
          .post(`${API}/${d.id}/correct-submitted`, this.correctionBody(d, reason))
          .subscribe({ next: done, error: fail });
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
