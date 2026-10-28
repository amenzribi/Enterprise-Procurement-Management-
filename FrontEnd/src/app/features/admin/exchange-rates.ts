import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { PageHeader } from '../../shared/ui/page-header';
import { Skeleton } from '../../shared/ui/skeleton';
import { ExchangeRate } from '../approvals/approval.models';
import { ApprovalsApi } from '../approvals/approvals.api';
import { todayIso } from '../approvals/absences';
import { decimalPlaces, normaliseDecimal } from '../requests/money';
import { CurrencyOption } from '../requests/request.models';
import { RequestsApi } from '../requests/requests.api';

type PageState = 'loading' | 'ready' | 'error';

/** Rate: a positive decimal with at most 8 decimals (DECIMAL(19,8)). Exported for tests. */
export const rateValidator: ValidatorFn = (control): ValidationErrors | null => {
  const value = normaliseDecimal(control.value as string);
  if (value === null || Number(value) <= 0) {
    return { rate: true };
  }
  return decimalPlaces(value) > 8 ? { rateDecimals: true } : null;
};

const differentCurrencies: ValidatorFn = (group): ValidationErrors | null =>
  group.get('baseCurrency')?.value && group.get('baseCurrency')?.value === group.get('quoteCurrency')?.value ? { sameCurrency: true } : null;

/**
 * Exchange rates used to compare request amounts with approval thresholds in company currency (S2-AC-03). A rate is
 * "quote currency units per 1 base currency unit" on a date. Recorded rates are never edited: a correction is a new
 * entry (S2DR-06).
 */
@Component({
  selector: 'app-exchange-rates',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, PageHeader, ReactiveFormsModule, Skeleton, TranslatePipe],
  template: `
    <app-page-header [heading]="'rates.title' | translate" [description]="'rates.description' | translate" />

    <section class="panel" aria-labelledby="rate-new">
      <h2 class="panel-title" id="rate-new">{{ 'rates.new' | translate }}</h2>
      <form class="form" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <div class="grid">
          <div class="field">
            <label for="rate-base">{{ 'rates.base' | translate }} *</label>
            <select id="rate-base" formControlName="baseCurrency" [attr.aria-invalid]="submitted() && form.controls.baseCurrency.invalid">
              <option value="">{{ 'requests.form.choose' | translate }}</option>
              @for (currency of currencies(); track currency.code) {
                <option [value]="currency.code">{{ currency.code }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="rate-quote">{{ 'rates.quote' | translate }} *</label>
            <select id="rate-quote" formControlName="quoteCurrency" [attr.aria-invalid]="submitted() && (form.controls.quoteCurrency.invalid || form.hasError('sameCurrency'))">
              <option value="">{{ 'requests.form.choose' | translate }}</option>
              @for (currency of currencies(); track currency.code) {
                <option [value]="currency.code">{{ currency.code }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="rate-value">{{ 'rates.rate' | translate }} *</label>
            <input id="rate-value" type="text" inputmode="decimal" dir="ltr" formControlName="rate" [attr.aria-invalid]="submitted() && form.controls.rate.invalid" aria-describedby="rate-hint" />
          </div>
          <div class="field">
            <label for="rate-date">{{ 'rates.date' | translate }} *</label>
            <input id="rate-date" type="date" formControlName="rateDate" [attr.aria-invalid]="submitted() && form.controls.rateDate.invalid" />
          </div>
          <div class="field">
            <label for="rate-source">{{ 'rates.source' | translate }}</label>
            <input id="rate-source" type="text" formControlName="source" maxlength="100" />
          </div>
        </div>
        <p id="rate-hint" class="muted small">
          @if (form.controls.baseCurrency.value && form.controls.quoteCurrency.value) {
            {{ 'rates.meaning' | translate: { base: form.controls.baseCurrency.value, quote: form.controls.quoteCurrency.value, rate: form.controls.rate.value || '…' } }}
          } @else {
            {{ 'rates.hint' | translate }}
          }
        </p>
        <p class="error small" role="alert">
          @if (submitted() && form.hasError('sameCurrency')) {
            {{ 'rates.errors.same-currency' | translate }}
          } @else if (submitted() && form.controls.rate.hasError('rateDecimals')) {
            {{ 'requests.errors.max-decimals' | translate: { max: 8 } }}
          } @else if (submitted() && form.invalid) {
            {{ 'rates.errors.invalid' | translate }}
          } @else if (error(); as code) {
            {{ 'errors.' + code | translate }}
          }
        </p>
        <button appButton type="submit" [disabled]="saving()">
          <app-icon name="plus" [size]="16" />
          {{ (saving() ? 'requests.form.saving' : 'rates.add') | translate }}
        </button>
      </form>
    </section>

    <section class="panel" aria-labelledby="rate-list">
      <h2 class="panel-title" id="rate-list">{{ 'rates.recorded' | translate }}</h2>
      @switch (state()) {
        @case ('loading') {
          <app-skeleton height="6rem" />
        }
        @case ('error') {
          <app-error-state [heading]="'rates.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="load()" />
        }
        @default {
          @if (rates().length === 0) {
            <app-empty-state icon="coins" [heading]="'rates.empty.title' | translate" [message]="'rates.empty.message' | translate" />
          } @else {
            <div class="table-wrap" tabindex="0" role="region" aria-labelledby="rate-list">
              <table>
                <thead>
                  <tr>
                    <th scope="col">{{ 'rates.date' | translate }}</th>
                    <th scope="col">{{ 'rates.pair' | translate }}</th>
                    <th scope="col" class="numeric">{{ 'rates.rate' | translate }}</th>
                    <th scope="col">{{ 'rates.source' | translate }}</th>
                  </tr>
                </thead>
                <tbody>
                  @for (rate of rates(); track rate.id) {
                    <tr>
                      <td class="tabular">{{ rate.rateDate | date: 'mediumDate' : undefined : language.locale() }}</td>
                      <td><bdi>1 {{ rate.baseCurrency }} = {{ rate.quoteCurrency }}</bdi></td>
                      <td class="numeric tabular"><bdi>{{ rate.rate }}</bdi></td>
                      <td>{{ rate.source ?? '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        }
      }
    </section>
  `,
  styleUrl: './admin.scss',
})
export class ExchangeRatesPage {
  private readonly api = inject(ApprovalsApi);
  private readonly requests = inject(RequestsApi);
  private readonly notifications = inject(NotificationService);
  protected readonly language = inject(LanguageService);

  protected readonly state = signal<PageState>('loading');
  protected readonly rates = signal<ExchangeRate[]>([]);
  protected readonly currencies = signal<CurrencyOption[]>([]);
  protected readonly saving = signal(false);
  protected readonly submitted = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup(
    {
      baseCurrency: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      quoteCurrency: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      rate: new FormControl('', { nonNullable: true, validators: [Validators.required, rateValidator] }),
      rateDate: new FormControl(todayIso(), { nonNullable: true, validators: [Validators.required] }),
      source: new FormControl('', { nonNullable: true }),
    },
    { validators: differentCurrencies },
  );

  constructor() {
    this.load();
    this.requests.formOptions().subscribe({ next: (options) => this.currencies.set(options.currencies), error: () => undefined });
  }

  load(): void {
    this.state.set('loading');
    this.api.exchangeRates().subscribe({
      next: (rates) => {
        this.rates.set([...rates].sort((a, b) => b.rateDate.localeCompare(a.rateDate)));
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  protected save(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .addExchangeRate({
        baseCurrency: value.baseCurrency,
        quoteCurrency: value.quoteCurrency,
        rate: normaliseDecimal(value.rate)!,
        rateDate: value.rateDate,
        source: value.source.trim() || null,
      })
      .subscribe({
        next: (rate) => {
          this.saving.set(false);
          this.submitted.set(false);
          this.rates.update((rates) => [rate, ...rates]);
          this.form.patchValue({ rate: '', source: '' });
          this.notifications.success('rates.saved');
        },
        error: (error: unknown) => {
          this.saving.set(false);
          const problem = toApiProblem(error);
          if (problem.status !== 401) {
            this.error.set(problem.code);
          }
        },
      });
  }
}
