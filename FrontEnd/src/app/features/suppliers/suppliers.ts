import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { StatusBadge } from '../../shared/ui/status-badge';
import { Supplier, SuppliersApi, supplierVisibilityKey } from './suppliers.api';

type PageState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-suppliers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, RouterLink, Skeleton, StatusBadge, TranslatePipe],
  template: `
    <app-page-header [heading]="'suppliers.title' | translate" [description]="'suppliers.description' | translate">
      @if (canImport()) {
        <a appButton variant="secondary" routerLink="/admin/import">
          <app-icon name="upload" [size]="16" />
          {{ 'nav.items.import' | translate }}
        </a>
      }
    </app-page-header>

    <div class="toolbar" role="search">
      <div class="field">
        <label for="supplier-search" class="visually-hidden">{{ 'suppliers.search' | translate }}</label>
        <input id="supplier-search" type="search" [value]="q()" (input)="onSearch($any($event.target).value)" [placeholder]="'suppliers.searchPlaceholder' | translate" />
      </div>
      <div class="field">
        <label for="supplier-visibility">{{ 'suppliers.visibilityLabel' | translate }}</label>
        <select id="supplier-visibility" (change)="onVisibility($any($event.target).value)">
          <option value="">{{ 'purchasing.inbox.all' | translate }}</option>
          <option value="SHARED">{{ 'suppliers.visibility.SHARED' | translate }}</option>
          <option value="COMPANY">{{ 'suppliers.visibility.COMPANY' | translate }}</option>
        </select>
      </div>
    </div>

    @if (canManage()) {
      <section class="panel" [class.editor]="editing()" aria-labelledby="supplier-new">
        <h2 class="panel-title" id="supplier-new">{{ (editing() ? 'suppliers.edit' : 'suppliers.new') | translate }}</h2>
        <form class="form" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <div class="grid">
            <div class="field">
              <label for="sup-code">{{ 'suppliers.code' | translate }} *</label>
              <input id="sup-code" formControlName="code" maxlength="40" />
            </div>
            <div class="field">
              <label for="sup-name">{{ 'suppliers.legalName' | translate }} *</label>
              <input id="sup-name" formControlName="legalName" maxlength="200" />
            </div>
            <div class="field">
              <label for="sup-country">{{ 'suppliers.country' | translate }} *</label>
              <input id="sup-country" formControlName="countryCode" maxlength="2" />
            </div>
            <div class="field">
              <label for="sup-vis">{{ 'suppliers.visibilityLabel' | translate }} *</label>
              <select id="sup-vis" formControlName="visibility">
                <option value="SHARED">{{ 'suppliers.visibility.SHARED' | translate }}</option>
                <option value="COMPANY">{{ 'suppliers.visibility.COMPANY' | translate }}</option>
              </select>
            </div>
            <div class="field">
              <label for="sup-tax">{{ 'suppliers.taxId' | translate }}</label>
              <input id="sup-tax" formControlName="taxId" maxlength="40" />
            </div>
            <div class="field">
              <label for="sup-city">{{ 'suppliers.city' | translate }}</label>
              <input id="sup-city" formControlName="city" maxlength="80" />
            </div>
          </div>
          <p class="error small" role="alert">@if (formError(); as code) { {{ 'errors.' + code | translate }} }</p>
          <div class="choices">
            <button appButton type="submit" [disabled]="saving()">{{ (saving() ? 'requests.form.saving' : 'suppliers.save') | translate }}</button>
            @if (editing()) {
              <button appButton variant="secondary" type="button" (click)="cancelEdit()">{{ 'common.cancel' | translate }}</button>
            }
          </div>
        </form>
      </section>
    }

    @switch (state()) {
      @case ('loading') { <app-skeleton height="8rem" /> }
      @case ('error') {
        <app-error-state [heading]="'suppliers.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="load()" />
      }
      @default {
        @if (items().length === 0) {
          <app-empty-state icon="users" [heading]="'suppliers.empty.title' | translate" [message]="'suppliers.empty.message' | translate" />
        } @else {
          <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'suppliers.title' | translate">
            <table>
              <thead>
                <tr>
                  <th scope="col">{{ 'suppliers.code' | translate }}</th>
                  <th scope="col">{{ 'suppliers.legalName' | translate }}</th>
                  <th scope="col">{{ 'suppliers.country' | translate }}</th>
                  <th scope="col">{{ 'suppliers.visibilityLabel' | translate }}</th>
                  <th scope="col">{{ 'suppliers.statusLabel' | translate }}</th>
                  @if (canManage()) {
                    <th scope="col"></th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (supplier of items(); track supplier.id) {
                  <tr>
                    <td class="tabular">{{ supplier.code }}</td>
                    <td>{{ supplier.legalName }}</td>
                    <td>{{ supplier.countryCode }}</td>
                    <td>
                      <app-status-badge [tone]="supplier.visibility === 'SHARED' ? 'info' : 'neutral'">
                        {{ visibilityKey(supplier.visibility) | translate }}
                      </app-status-badge>
                    </td>
                    <td>
                      <app-status-badge [tone]="supplier.active ? 'success' : 'neutral'">
                        {{ (supplier.active ? 'suppliers.active' : 'suppliers.inactive') | translate }}
                      </app-status-badge>
                    </td>
                    @if (canManage()) {
                      <td>
                        <button appButton variant="secondary" size="small" type="button" (click)="edit(supplier)">{{ 'requests.detail.edit' | translate }}</button>
                        @if (supplier.active) {
                          <button appButton variant="secondary" size="small" type="button" (click)="deactivate(supplier)">{{ 'suppliers.deactivate' | translate }}</button>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      }
    }
  `,
  styleUrl: '../admin/admin.scss',
})
export class SuppliersPage {
  private readonly api = inject(SuppliersApi);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly state = signal<PageState>('loading');
  protected readonly items = signal<Supplier[]>([]);
  protected readonly q = signal('');
  protected readonly visibility = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly editing = signal<Supplier | null>(null);
  protected readonly visibilityKey = supplierVisibilityKey;

  protected readonly form = new FormGroup({
    code: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    legalName: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    countryCode: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2), Validators.maxLength(2)] }),
    visibility: new FormControl('SHARED', { nonNullable: true }),
    taxId: new FormControl(''),
    city: new FormControl(''),
  });

  protected readonly canManage = computed(() => hasPermission(this.auth.me(), 'SUPPLIER_MANAGE'));
  protected readonly canImport = computed(() => hasPermission(this.auth.me(), 'IMPORT_RUN'));

  constructor() {
    this.load();
  }

  protected onSearch(value: string): void {
    this.q.set(value.trim());
    this.load();
  }

  protected onVisibility(value: string): void {
    this.visibility.set(value || null);
    this.load();
  }

  protected load(): void {
    this.state.set('loading');
    this.api.list(this.q() || null, this.visibility(), 0).subscribe({
      next: (page) => {
        this.items.set(page.items);
        this.state.set('ready');
      },
      error: (error: unknown) => {
        const problem = toApiProblem(error);
        if (problem.status !== 401) {
          this.state.set('error');
        }
      },
    });
  }

  protected edit(supplier: Supplier): void {
    this.editing.set(supplier);
    this.form.reset({
      code: supplier.code,
      legalName: supplier.legalName,
      countryCode: supplier.countryCode,
      visibility: supplier.visibility,
      taxId: supplier.taxId ?? '',
      city: supplier.city ?? '',
    });
  }

  protected cancelEdit(): void {
    this.editing.set(null);
    this.form.reset({ code: '', legalName: '', countryCode: '', visibility: 'SHARED', taxId: '', city: '' });
    this.formError.set(null);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('validation-failed');
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    const value = this.form.getRawValue();
    const body = {
      code: value.code.trim(),
      legalName: value.legalName.trim(),
      countryCode: value.countryCode.trim().toUpperCase(),
      visibility: value.visibility,
      owningCompanyId: value.visibility === 'COMPANY' ? (this.auth.me()?.company.id ?? null) : null,
      taxId: value.taxId?.trim() || null,
      city: value.city?.trim() || null,
      version: this.editing()?.version,
    };
    const key = newIdempotencyKey();
    const call = this.editing()
      ? this.api.update(this.editing()!.id, body, key)
      : this.api.create(body, key);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success('suppliers.saved');
        this.cancelEdit();
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.formError.set(toApiProblem(error).code);
      },
    });
  }

  protected deactivate(supplier: Supplier): void {
    this.api.deactivate(supplier.id, newIdempotencyKey()).subscribe({
      next: () => {
        this.notifications.success('suppliers.deactivated');
        this.load();
      },
      error: (error: unknown) => this.notifications.error('errors.' + toApiProblem(error).code),
    });
  }
}
