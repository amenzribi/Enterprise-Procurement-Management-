import { DOCUMENT, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { newIdempotencyKey } from '../../core/api/idempotency';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { LanguageSelect } from '../../shared/ui/language-select';
import { formatMoney, minorUnitsOf } from '../requests/money';
import { LinkSummary } from './approval.models';
import { ApprovalsApi } from './approvals.api';

type LinkState = 'loading' | 'ready' | 'approving' | 'approved' | 'invalid' | 'unavailable';

/**
 * Reads the one-tap token from the URL fragment and removes it from the address bar and history straight away, so
 * it is never sent to a server, kept in history or passed on in a Referer header (S2DR-07). Exported for tests.
 */
export function takeTokenFromFragment(document: Document): string | null {
  const view = document.defaultView;
  if (!view) {
    return null;
  }
  const token = view.location.hash.replace(/^#/, '').trim();
  if (view.location.hash) {
    view.history.replaceState(view.history.state, '', view.location.pathname + view.location.search);
  }
  return /^[A-Za-z0-9_-]{20,512}$/.test(token) ? token : null;
}

/**
 * One-tap approval from the e-mail on a phone (S2-AC-07). Opening the page never approves: it shows a short summary
 * and one confirm button. Every invalid situation (expired, used, decided, reassigned, unknown) shows the same
 * neutral message, so the page reveals nothing about the link. Rejecting, returning, asking or delegating needs
 * signing in ("Review in the application").
 */
@Component({
  selector: 'app-one-tap-approval',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, Icon, LanguageSelect, RouterLink, TranslatePipe],
  template: `
    <div class="page">
      <header class="top">
        <span class="brand"><span class="brand-mark" aria-hidden="true">P</span>{{ 'app.name' | translate }}</span>
        <app-language-select />
      </header>

      <main id="main" class="card" tabindex="-1">
        @switch (state()) {
          @case ('loading') {
            <h1 class="title">{{ 'oneTap.title' | translate }}</h1>
            <p class="muted" role="status">{{ 'oneTap.loading' | translate }}</p>
          }

          @case ('invalid') {
            <app-icon name="lock" [size]="32" class="state-icon" />
            <h1 class="title">{{ 'oneTap.invalid.title' | translate }}</h1>
            <p class="muted">{{ 'oneTap.invalid.message' | translate }}</p>
            <a appButton routerLink="/approvals">{{ 'oneTap.openApp' | translate }}</a>
          }

          @case ('unavailable') {
            <app-icon name="triangle-alert" [size]="32" class="state-icon" />
            <h1 class="title">{{ 'oneTap.unavailable.title' | translate }}</h1>
            <p class="muted" role="alert">{{ 'oneTap.unavailable.message' | translate }}</p>
          }

          @case ('approved') {
            <app-icon name="circle-check" [size]="40" class="state-icon is-success" />
            <h1 class="title">{{ 'oneTap.approved.title' | translate }}</h1>
            <p class="muted" role="status">
              {{ 'oneTap.approved.message' | translate: { number: summary()?.number ?? '' } }}
            </p>
            <a appButton variant="secondary" routerLink="/approvals">{{ 'oneTap.openApp' | translate }}</a>
          }

          @default {
            @if (summary(); as s) {
              <p class="eyebrow">{{ 'oneTap.title' | translate }}</p>
              <p class="number tabular"><bdi>{{ s.number }}</bdi></p>
              <h1 class="title">{{ s.title }}</h1>
              <dl class="facts">
                @if (s.requester) {
                  <div><dt>{{ 'requests.fields.requester' | translate }}</dt><dd>{{ s.requester.displayName }}</dd></div>
                }
                @if (s.company) {
                  <div><dt>{{ 'requests.fields.requestingCompany' | translate }}</dt><dd>{{ s.company.name }}</dd></div>
                }
                <div>
                  <dt>{{ 'requests.detail.total' | translate }}</dt>
                  <dd class="amount tabular"><bdi>{{ money(s) }}</bdi></dd>
                </div>
                @if (s.requiredDate) {
                  <div><dt>{{ 'requests.fields.requiredDate' | translate }}</dt><dd class="tabular">{{ s.requiredDate | date: 'mediumDate' : undefined : language.locale() }}</dd></div>
                }
                @if (s.stageName) {
                  <div><dt>{{ 'oneTap.stage' | translate }}</dt><dd>{{ s.stageName }}</dd></div>
                }
              </dl>
              @if (s.lines?.length) {
                <h2 class="lines-title">{{ 'requests.form.sections.lines' | translate }}</h2>
                <ul class="lines">
                  @for (line of s.lines; track $index) {
                    <li><bdi class="tabular qty">{{ line.quantity }} {{ line.unit ?? '' }}</bdi> — <bdi>{{ line.description }}</bdi></li>
                  }
                </ul>
                @if ((s.lineCount ?? 0) > s.lines!.length) {
                  <p class="muted small">{{ 'oneTap.moreLines' | translate: { count: s.lineCount! - s.lines!.length } }}</p>
                }
              }
              <div class="actions">
                <button appButton type="button" class="approve" [disabled]="state() === 'approving'" (click)="approve()">
                  <app-icon name="circle-check" [size]="18" />
                  {{ (state() === 'approving' ? 'approvals.decide.sending' : 'oneTap.approve') | translate }}
                </button>
                <a appButton variant="secondary" routerLink="/approvals">{{ 'oneTap.review' | translate }}</a>
              </div>
              <p class="muted small">{{ 'oneTap.reviewHint' | translate }}</p>
              @if (s.expiresAt) {
                <p class="muted small">
                  {{ 'oneTap.expires' | translate }}
                  <time [attr.datetime]="s.expiresAt">{{ s.expiresAt | date: 'medium' : undefined : language.locale() }}</time>
                </p>
              }
            }
          }
        }
      </main>
    </div>
  `,
  styles: `
    .page {
      display: grid;
      align-content: start;
      gap: var(--space-6);
      min-block-size: 100dvh;
      padding: var(--space-4);
      background: var(--color-paper);
    }

    .top {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      inline-size: min(34rem, 100%);
      margin-inline: auto;
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      font-weight: var(--weight-semibold);
    }

    .brand-mark {
      display: inline-grid;
      place-items: center;
      inline-size: 2rem;
      block-size: 2rem;
      border-radius: var(--radius-sm);
      background: var(--color-nav);
      color: var(--color-nav-text-strong);
      font-size: var(--text-xs);
    }

    .card {
      display: grid;
      gap: var(--space-4);
      inline-size: min(34rem, 100%);
      margin-inline: auto;
      padding: var(--space-6);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
    }

    .card:focus {
      outline: none;
    }

    .state-icon {
      color: var(--color-text-muted);
    }

    .state-icon.is-success {
      color: var(--color-success);
    }

    .eyebrow {
      color: var(--color-text-muted);
      font-size: var(--text-sm);
    }

    .number {
      color: var(--color-accent-hover);
      font-weight: var(--weight-semibold);
    }

    .title {
      font-size: var(--text-lg);
      overflow-wrap: anywhere;
    }

    .facts {
      display: grid;
      gap: var(--space-2);
      margin: 0;
    }

    .facts div {
      display: flex;
      justify-content: space-between;
      gap: var(--space-3);
      padding-block: var(--space-2);
      border-block-end: 1px solid var(--color-line);
    }

    .facts dt {
      color: var(--color-text-muted);
    }

    .facts dd {
      margin: 0;
      text-align: end;
    }

    .amount {
      font-size: var(--text-md);
      font-weight: var(--weight-semibold);
    }

    .lines-title {
      font-size: var(--text-base);
    }

    .lines {
      display: grid;
      gap: var(--space-1);
      margin: 0;
      padding-inline-start: var(--space-5);
    }

    .qty {
      font-weight: var(--weight-semibold);
    }

    .actions {
      display: grid;
      gap: var(--space-2);
    }

    .actions > * {
      min-block-size: 3rem;
    }
  `,
})
export class OneTapApprovalPage {
  private readonly api = inject(ApprovalsApi);
  protected readonly language = inject(LanguageService);

  protected readonly state = signal<LinkState>('loading');
  protected readonly summary = signal<LinkSummary | null>(null);

  /** Kept in memory only: never in a signal shown by the template, a URL, storage or a log. */
  private readonly token = takeTokenFromFragment(inject(DOCUMENT));
  private approvalKey: string | null = null;

  constructor() {
    if (!this.token) {
      this.state.set('invalid');
      return;
    }
    this.api.linkSummary(this.token).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.state.set('ready');
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  protected approve(): void {
    if (!this.token || this.state() !== 'ready') {
      return;
    }
    // One key for this confirmation: a double tap or a retry is answered with the first result.
    this.approvalKey ??= newIdempotencyKey();
    this.state.set('approving');
    this.api.linkApprove(this.token, this.approvalKey).subscribe({
      next: (result) => {
        if (result?.number) {
          this.summary.set({ ...this.summary()!, number: result.number });
        }
        this.state.set('approved');
      },
      error: (error: unknown) => this.fail(error),
    });
  }

  protected money(summary: LinkSummary): string {
    return formatMoney(summary.amount ?? null, summary.currency, summary.currencyMinorUnits ?? minorUnitsOf(summary.currency), this.language.locale()) || '—';
  }

  private fail(error: unknown): void {
    const problem = toApiProblem(error);
    // Network failures and server errors may pass: offer to try later. Everything else is the same neutral message.
    this.state.set(problem.status === 0 || problem.status >= 500 || problem.status === 429 ? 'unavailable' : 'invalid');
  }
}
