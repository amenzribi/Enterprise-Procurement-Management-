import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { StatusBadge } from '../../shared/ui/status-badge';
import { CompanyRow } from '../organization/organization-admin';

export interface ImportRow {
  status: string;
  entityId?: number | null;
  errors?: string | null;
}

export interface ImportResult {
  id: number;
  status: string;
  accepted: number;
  rejected: number;
  rows: ImportRow[];
}

/** Tone of an import row for the result table. Exported for tests. */
export function importRowTone(status: string): 'success' | 'danger' | 'neutral' {
  if (status === 'ACCEPTED') {
    return 'success';
  }
  if (status === 'REJECTED') {
    return 'danger';
  }
  return 'neutral';
}

@Component({
  selector: 'app-import-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, EmptyState, Icon, PageHeader, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'imports.title' | translate" [description]="'imports.description' | translate" />

    <section class="panel" aria-labelledby="import-form">
      <h2 class="panel-title" id="import-form">{{ 'imports.upload' | translate }}</h2>
      <form class="form" (submit)="$event.preventDefault(); submit()" novalidate>
        <div class="grid">
          <div class="field">
            <label for="import-kind">{{ 'imports.kind' | translate }} *</label>
            <select id="import-kind" [value]="kind()" (change)="kind.set($any($event.target).value)">
              <option value="SUPPLIER">{{ 'imports.kinds.SUPPLIER' | translate }}</option>
              @if (canOrg()) {
                <option value="ORGANISATION">{{ 'imports.kinds.ORGANISATION' | translate }}</option>
              }
            </select>
          </div>
          @if (kind() === 'ORGANISATION') {
            <div class="field">
              <label for="import-company">{{ 'imports.company' | translate }} *</label>
              <select id="import-company" [value]="companyId() ?? ''" (change)="companyId.set(toId($any($event.target).value))">
                <option value="">{{ 'requests.form.choose' | translate }}</option>
                @for (company of companies(); track company.id) {
                  <option [value]="company.id">{{ company.code }} — {{ company.legalName }}</option>
                }
              </select>
            </div>
          }
          <div class="field">
            <label for="import-file">{{ 'imports.file' | translate }} *</label>
            <div class="file-wrap">
              <input id="import-file" type="file" accept=".csv,text/csv" (change)="onFile($event)" />
            </div>
          </div>
        </div>
        <p class="muted small hint">{{ (kind() === 'SUPPLIER' ? 'imports.hintSupplier' : 'imports.hintOrg') | translate }}</p>
        <p class="error small" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>
        <button appButton type="submit" [disabled]="busy() || !file()">
          <app-icon name="upload" [size]="16" />
          {{ (busy() ? 'imports.uploading' : 'imports.run') | translate }}
        </button>
      </form>
    </section>

    @if (result(); as job) {
      <section class="panel" aria-labelledby="import-result">
        <h2 class="panel-title" id="import-result">{{ 'imports.result' | translate }}</h2>
        <p>
          <app-status-badge [tone]="job.status === 'COMMITTED' ? 'success' : 'danger'">{{ 'imports.status.' + job.status | translate }}</app-status-badge>
          {{ 'imports.counts' | translate: { accepted: job.accepted, rejected: job.rejected } }}
        </p>
        @if (job.rows.length === 0) {
          <app-empty-state icon="file-text" [heading]="'imports.noRows.title' | translate" [message]="'imports.noRows.message' | translate" />
        } @else {
          <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'imports.result' | translate">
            <table>
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">{{ 'imports.rowStatus' | translate }}</th>
                  <th scope="col">{{ 'imports.rowErrors' | translate }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of job.rows; track $index; let i = $index) {
                  <tr>
                    <td class="tabular">{{ i + 1 }}</td>
                    <td>
                      <app-status-badge [tone]="tone(row.status)">{{ 'imports.row.' + row.status | translate }}</app-status-badge>
                    </td>
                    <td>{{ row.errors ?? '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }
  `,
  styleUrl: '../admin/admin.scss',
  styles: `
    .hint { overflow-wrap: anywhere; }
    .panel, .form, .grid { min-inline-size: 0; }
    .file-wrap { overflow: hidden; max-inline-size: 100%; }
  `,
})
export class ImportPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly kind = signal<'SUPPLIER' | 'ORGANISATION'>('SUPPLIER');
  protected readonly companyId = signal<number | null>(null);
  protected readonly file = signal<File | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<ImportResult | null>(null);
  protected readonly companies = signal<CompanyRow[]>([]);
  protected readonly canOrg = computed(() => hasPermission(this.auth.me(), 'ORG_ADMIN'));
  protected readonly tone = importRowTone;

  constructor() {
    if (this.canOrg()) {
      this.http.get<CompanyRow[]>('/api/v1/admin/organization/companies').subscribe({
        next: (rows) => this.companies.set(rows),
        error: () => this.companies.set([]),
      });
    }
  }

  protected toId(value: string): number | null {
    const id = Number(value);
    return Number.isFinite(id) && id > 0 ? id : null;
  }

  protected onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
  }

  protected submit(): void {
    const file = this.file();
    if (!file) {
      this.error.set('validation-failed');
      return;
    }
    if (this.kind() === 'ORGANISATION' && !this.companyId()) {
      this.error.set('validation-failed');
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const data = new FormData();
    data.append('file', file, file.name);
    let params = new HttpParams().set('kind', this.kind());
    if (this.kind() === 'ORGANISATION' && this.companyId()) {
      params = params.set('companyId', this.companyId()!);
    }
    this.http.post<ImportResult>('/api/v1/imports', data, { params }).subscribe({
      next: (job) => {
        this.busy.set(false);
        this.result.set(job);
        this.notifications.success('imports.committed', { accepted: job.accepted });
      },
      error: (error: unknown) => {
        this.busy.set(false);
        const problem = toApiProblem(error);
        this.error.set(problem.code);
        const body = error instanceof HttpErrorResponse && error.error && typeof error.error === 'object' ? error.error : {};
        const rows = Array.isArray((body as ImportResult).rows) ? (body as ImportResult).rows : [];
        if (rows.length || problem.code === 'import-rejected') {
          this.result.set({
            id: Number((body as { jobId?: number }).jobId) || 0,
            status: 'REJECTED',
            accepted: 0,
            rejected: Number((body as { rejected?: number }).rejected) || rows.length,
            rows,
          });
        }
      },
    });
  }
}
