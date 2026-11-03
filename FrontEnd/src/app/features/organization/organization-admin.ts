import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey, idempotencyHeaders } from '../../core/api/idempotency';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';

export interface CompanyRow {
  id: number;
  code: string;
  legalName: string;
  companyType: string;
  countryCode: string;
  currencyCode: string;
  defaultLanguage: string;
  timeZone: string;
  purchasingMode: string;
  parentCompanyId?: number | null;
  erpCompanyCode?: string | null;
  active: boolean;
  version: number;
}

export interface UnitRow {
  id: number;
  companyId: number;
  code: string;
  name: string;
  active: boolean;
}

type PageState = 'loading' | 'ready' | 'error';
type UnitKind = 'sites' | 'departments' | 'cost-centres';

const API = '/api/v1/admin/organization';

@Component({
  selector: 'app-organization-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'org.title' | translate" [description]="'org.description' | translate">
      <a appButton variant="secondary" routerLink="/admin/import">
        <app-icon name="upload" [size]="16" />
        {{ 'nav.items.import' | translate }}
      </a>
    </app-page-header>

    @switch (state()) {
      @case ('loading') { <app-skeleton height="8rem" /> }
      @case ('error') {
        <app-error-state [heading]="'org.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="load()" />
      }
      @default {
        <section class="panel" aria-labelledby="org-companies">
          <h2 class="panel-title" id="org-companies">{{ 'org.companies' | translate }}</h2>
          @if (companies().length === 0) {
            <app-empty-state icon="building" [heading]="'org.empty.title' | translate" [message]="'org.empty.message' | translate" />
          } @else {
            <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'org.companies' | translate">
              <table>
                <thead>
                  <tr>
                    <th scope="col">{{ 'org.code' | translate }}</th>
                    <th scope="col">{{ 'org.legalName' | translate }}</th>
                    <th scope="col">{{ 'org.country' | translate }}</th>
                    <th scope="col">{{ 'org.currency' | translate }}</th>
                    <th scope="col">{{ 'org.purchasingMode' | translate }}</th>
                    <th scope="col">{{ 'org.statusLabel' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (company of companies(); track company.id) {
                    <tr>
                      <td>
                        <button type="button" class="linkish" [attr.aria-pressed]="selected()?.id === company.id" (click)="select(company)">
                          {{ company.code }}
                        </button>
                      </td>
                      <td>{{ company.legalName }}</td>
                      <td>{{ company.countryCode }}</td>
                      <td>{{ company.currencyCode }}</td>
                      <td>{{ 'purchasing.mode.' + company.purchasingMode | translate }}</td>
                      <td>
                        <app-status-badge [tone]="company.active ? 'success' : 'neutral'">
                          {{ (company.active ? 'org.active' : 'org.inactive') | translate }}
                        </app-status-badge>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>

        <section class="panel" aria-labelledby="org-new-company">
          <h2 class="panel-title" id="org-new-company">{{ 'org.newCompany' | translate }}</h2>
          <p class="muted">{{ 'org.companyVsSite' | translate }}</p>
          <p class="muted">{{ 'org.workingCalendarNote' | translate }}</p>
          <p class="muted">{{ 'org.deactivateNote' | translate }}</p>
          <form class="form" [formGroup]="companyForm" (ngSubmit)="createCompany()" novalidate>
            <div class="grid">
              <div class="field"><label for="co-code">{{ 'org.code' | translate }} *</label><input id="co-code" formControlName="code" maxlength="40" /></div>
              <div class="field"><label for="co-name">{{ 'org.legalName' | translate }} *</label><input id="co-name" formControlName="legalName" maxlength="200" /></div>
              <div class="field"><label for="co-type">{{ 'org.type' | translate }} *</label>
                <select id="co-type" formControlName="companyType">
                  <option value="SUBSIDIARY">SUBSIDIARY</option>
                  <option value="PARENT">PARENT</option>
                  <option value="PURCHASING_CENTER">PURCHASING_CENTER</option>
                </select>
              </div>
              <div class="field"><label for="co-country">{{ 'org.country' | translate }} *</label><input id="co-country" formControlName="countryCode" maxlength="2" /></div>
              <div class="field"><label for="co-currency">{{ 'org.currency' | translate }} *</label><input id="co-currency" formControlName="currencyCode" maxlength="3" /></div>
              <div class="field"><label for="co-lang">{{ 'org.language' | translate }} *</label>
                <select id="co-lang" formControlName="defaultLanguage">
                  <option value="en">en</option>
                  <option value="fr">fr</option>
                  <option value="ar">ar</option>
                </select>
              </div>
              <div class="field"><label for="co-tz">{{ 'org.timeZone' | translate }} *</label><input id="co-tz" formControlName="timeZone" /></div>
              <div class="field"><label for="co-mode">{{ 'org.purchasingMode' | translate }} *</label>
                <select id="co-mode" formControlName="purchasingMode">
                  <option value="DIRECT">{{ 'purchasing.mode.DIRECT' | translate }}</option>
                  <option value="CENTRALIZED">{{ 'purchasing.mode.CENTRALIZED' | translate }}</option>
                  <option value="ON_BEHALF">{{ 'purchasing.mode.ON_BEHALF' | translate }}</option>
                  <option value="MIXED">{{ 'purchasing.mode.MIXED' | translate }}</option>
                </select>
              </div>
            </div>
            <p class="error small" role="alert">@if (companyError(); as code) { {{ 'errors.' + code | translate }} }</p>
            <button appButton type="submit" [disabled]="saving()">{{ 'org.createCompany' | translate }}</button>
          </form>
        </section>

        @if (selected(); as company) {
          <section class="panel" aria-labelledby="org-units">
            <h2 class="panel-title" id="org-units">{{ 'org.unitsOf' | translate: { name: company.legalName } }}</h2>
            <div class="tabs" role="tablist">
              @for (kind of kinds; track kind) {
                <button type="button" class="tab" [attr.aria-selected]="unitKind() === kind" (click)="showUnits(kind)">
                  {{ 'org.' + kind | translate }}
                </button>
              }
            </div>
            <form class="form" [formGroup]="unitForm" (ngSubmit)="createUnit()" novalidate>
              <div class="grid">
                <div class="field"><label for="un-code">{{ 'org.code' | translate }} *</label><input id="un-code" formControlName="code" maxlength="40" /></div>
                <div class="field"><label for="un-name">{{ 'org.name' | translate }} *</label><input id="un-name" formControlName="name" maxlength="200" /></div>
              </div>
              <p class="error small" role="alert">@if (unitError(); as code) { {{ 'errors.' + code | translate }} }</p>
              <button appButton type="submit" [disabled]="saving()">{{ 'org.addUnit' | translate }}</button>
            </form>
            @if (units().length === 0) {
              <p class="muted">{{ 'org.noUnits' | translate }}</p>
            } @else {
              <ul class="unit-list">
                @for (unit of units(); track unit.id) {
                  <li><span class="tabular">{{ unit.code }}</span> — {{ unit.name }}</li>
                }
              </ul>
            }
          </section>
        }
      }
    }
  `,
  styleUrl: '../admin/admin.scss',
  styles: `
    .linkish {
      border: 0;
      padding: 0;
      background: none;
      color: var(--color-accent);
      text-decoration: underline;
      cursor: pointer;
    }
    .linkish[aria-pressed='true'] { font-weight: var(--weight-semibold); }
    .tabs { display: flex; flex-wrap: wrap; gap: var(--space-2); margin-block-end: var(--space-3); }
    .tab {
      min-block-size: 2.25rem;
      padding-inline: var(--space-3);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-sm);
      background: var(--color-surface);
      cursor: pointer;
    }
    .tab[aria-selected='true'] { background: var(--color-accent); color: var(--color-text-inverse); }
    .unit-list { margin: var(--space-3) 0 0; padding-inline-start: 1.25rem; }
    .muted { color: var(--color-text-muted); }
  `,
})
export class OrganizationAdminPage {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(NotificationService);

  protected readonly kinds: UnitKind[] = ['sites', 'departments', 'cost-centres'];
  protected readonly state = signal<PageState>('loading');
  protected readonly companies = signal<CompanyRow[]>([]);
  protected readonly selected = signal<CompanyRow | null>(null);
  protected readonly units = signal<UnitRow[]>([]);
  protected readonly unitKind = signal<UnitKind>('sites');
  protected readonly saving = signal(false);
  protected readonly companyError = signal<string | null>(null);
  protected readonly unitError = signal<string | null>(null);

  protected readonly companyForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    legalName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    companyType: new FormControl('SUBSIDIARY', { nonNullable: true }),
    countryCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    currencyCode: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    defaultLanguage: new FormControl('en', { nonNullable: true }),
    timeZone: new FormControl('Africa/Tunis', { nonNullable: true }),
    purchasingMode: new FormControl('MIXED', { nonNullable: true }),
  });

  protected readonly unitForm = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.http.get<CompanyRow[]>(`${API}/companies`).subscribe({
      next: (rows) => {
        this.companies.set(rows);
        this.state.set('ready');
      },
      error: (error: unknown) => {
        if (toApiProblem(error).status !== 401) {
          this.state.set('error');
        }
      },
    });
  }

  protected select(company: CompanyRow): void {
    this.selected.set(company);
    this.showUnits(this.unitKind());
  }

  protected showUnits(kind: UnitKind): void {
    this.unitKind.set(kind);
    const company = this.selected();
    if (!company) {
      return;
    }
    this.http.get<UnitRow[]>(`${API}/companies/${company.id}/${kind}`).subscribe({
      next: (rows) => this.units.set(rows),
      error: () => this.units.set([]),
    });
  }

  protected createCompany(): void {
    if (this.companyForm.invalid) {
      this.companyError.set('validation-failed');
      return;
    }
    this.saving.set(true);
    this.companyError.set(null);
    const body = this.companyForm.getRawValue();
    this.http.post<CompanyRow>(`${API}/companies`, body, idempotencyHeaders(newIdempotencyKey())).subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success('org.companyCreated');
        this.companyForm.reset({
          code: '',
          legalName: '',
          companyType: 'SUBSIDIARY',
          countryCode: '',
          currencyCode: '',
          defaultLanguage: 'en',
          timeZone: 'Africa/Tunis',
          purchasingMode: 'MIXED',
        });
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.companyError.set(toApiProblem(error).code);
      },
    });
  }

  protected createUnit(): void {
    const company = this.selected();
    if (!company || this.unitForm.invalid) {
      this.unitError.set('validation-failed');
      return;
    }
    this.saving.set(true);
    this.unitError.set(null);
    const value = this.unitForm.getRawValue();
    const body = { code: value.code, name: value.name, type: this.unitKind() === 'sites' ? 'BRANCH' : null };
    this.http
      .post<UnitRow>(`${API}/companies/${company.id}/${this.unitKind()}`, body, idempotencyHeaders(newIdempotencyKey()))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.notifications.success('org.unitCreated');
          this.unitForm.reset({ code: '', name: '' });
          this.showUnits(this.unitKind());
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.unitError.set(toApiProblem(error).code);
        },
      });
  }
}
