import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { Button } from '../../shared/ui/button';
import { ErrorState } from '../../shared/ui/error-state';
import { PageHeader } from '../../shared/ui/page-header';
import { PublicFrame } from '../../shared/ui/public-frame';
import { Skeleton } from '../../shared/ui/skeleton';
import { PublicSummary } from './sourcing.models';
import { SourcingApi } from './sourcing.api';

/**
 * Supplier quote entry via secure link (no account). Token is in the URL fragment (`/q#token`).
 */
@Component({
  selector: 'app-public-quote',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, ErrorState, PageHeader, PublicFrame, Skeleton, TranslatePipe],
  template: `
    <app-public-frame class="frame">
    <main class="page">
      <app-page-header [heading]="'sourcing.public.title' | translate" [description]="'sourcing.public.description' | translate" />
      @if (state() === 'loading') {
        <app-skeleton height="8rem" />
      } @else if (state() === 'invalid') {
        <app-error-state [heading]="'sourcing.public.invalidTitle' | translate" [message]="'sourcing.public.invalidMessage' | translate" />
      } @else if (summary(); as s) {
        <p class="meta">{{ s.supplierName }} · {{ s.title }}</p>
        @if (s.alreadySubmitted) {
          <p role="status">{{ 'sourcing.public.alreadySubmitted' | translate }}</p>
        } @else {
          <ul class="lines">
            @for (line of s.lines; track line.id) {
              <li>{{ line.lineNo }}. {{ line.description }} — {{ line.quantity }} {{ line.unit }}</li>
            }
          </ul>
          <form class="form" (submit)="$event.preventDefault(); submit()" novalidate>
            <label for="pq-currency">{{ 'sourcing.public.currency' | translate }} *</label>
            <input id="pq-currency" maxlength="3" [value]="currency()" (input)="currency.set($any($event.target).value)" />
            <label for="pq-price">{{ 'sourcing.actions.unitPrice' | translate }} *</label>
            <input id="pq-price" type="number" min="0" step="0.01" [value]="unitPrice()" (input)="unitPrice.set($any($event.target).value)" />
            <p class="error" role="alert">@if (error(); as code) { {{ 'errors.' + code | translate }} }</p>
            <button appButton type="submit" [disabled]="busy()">{{ 'sourcing.public.submit' | translate }}</button>
          </form>
        }
      }
    </main>
    </app-public-frame>
  `,
  styles: `
    .frame { --frame-width: 40rem; }
    .meta { margin-block-end: var(--space-4); font-weight: var(--weight-semibold); }
    .form { display: grid; gap: var(--space-2); }
    .form label { margin-block-start: var(--space-2); font-size: var(--text-sm); font-weight: var(--weight-semibold); }
    .form button { min-block-size: 3rem; }
    .lines { margin: 0 0 var(--space-4); padding: var(--space-3) var(--space-4) var(--space-3) var(--space-8); border-radius: var(--radius-md); background: var(--color-paper); }
    .lines li + li { margin-block-start: var(--space-1); }
    .error { min-block-size: 1.25rem; color: var(--color-danger); font-size: var(--text-sm); }
    input { min-block-size: 3rem; padding-inline: var(--space-4); font-size: var(--text-md); }
  `,
})
export class PublicQuotePage {
  private readonly api = inject(SourcingApi);

  protected readonly state = signal<'loading' | 'ready' | 'invalid'>('loading');
  protected readonly summary = signal<PublicSummary | null>(null);
  protected readonly currency = signal('EUR');
  protected readonly unitPrice = signal('0');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  private token = '';

  constructor() {
    this.token = window.location.hash.replace(/^#/, '').trim();
    if (!this.token) {
      this.state.set('invalid');
      return;
    }
    // Drop the fragment from the address bar after reading (S4SL-04), keep token in memory only.
    history.replaceState(null, '', window.location.pathname + window.location.search);
    this.api.publicSummary(this.token).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.state.set('ready');
      },
      error: () => this.state.set('invalid'),
    });
  }

  submit(): void {
    const summary = this.summary();
    if (!summary?.lines[0]) {
      return;
    }
    const price = Number(this.unitPrice());
    this.busy.set(true);
    this.error.set(null);
    this.api
      .publicSubmit({
        token: this.token,
        currency: this.currency().trim().toUpperCase(),
        freightAmount: 0,
        dutyAmount: 0,
        otherAmount: 0,
        lines: [{ rfqLineId: summary.lines[0].id, quantity: Number(summary.lines[0].quantity), unitPrice: Number.isFinite(price) ? price : null }],
      })
      .subscribe({
        next: (next) => {
          this.busy.set(false);
          this.summary.set(next);
        },
        error: (error: unknown) => {
          this.busy.set(false);
          const problem = toApiProblem(error);
          if (problem.status === 404) {
            this.state.set('invalid');
            return;
          }
          this.error.set(problem.code);
        },
      });
  }
}
