import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from '../../shared/ui/button';
import { PageHeader } from '../../shared/ui/page-header';
import { PublicFrame } from '../../shared/ui/public-frame';

/**
 * E-mail correction, proof of control (Sprint 2, ADR-016 step 5): the owner of the new mailbox opens the link sent
 * there. The token is in the URL fragment (never sent to servers or logs) and is posted only when the owner
 * confirms, so a mail scanner that opens links does not confirm the change.
 */
@Component({
  selector: 'app-email-change-confirm',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, PageHeader, PublicFrame, RouterLink, TranslatePipe],
  template: `
    <app-public-frame>
      <main class="wrap">
        <app-page-header [heading]="'emailChange.title' | translate" [description]="'emailChange.description' | translate" />
        @switch (state()) {
          @case ('done') {
            <p role="status">{{ 'emailChange.done' | translate }}</p>
            <a routerLink="/sign-in">{{ 'auth.signIn.title' | translate }}</a>
          }
          @case ('invalid') {
            <p role="alert">{{ 'emailChange.invalid' | translate }}</p>
            <a routerLink="/sign-in">{{ 'auth.signIn.title' | translate }}</a>
          }
          @case ('error') {
            <p role="alert">{{ 'emailChange.error' | translate }}</p>
            <button appButton type="button" [disabled]="busy()" (click)="confirm()">{{ 'common.retry' | translate }}</button>
          }
          @default {
            <button appButton type="button" [disabled]="busy()" (click)="confirm()">
              {{ 'emailChange.submit' | translate }}
            </button>
          }
        }
      </main>
    </app-public-frame>
  `,
  styles: `
    .wrap {
      display: grid;
      gap: var(--space-4);
    }
    button {
      min-block-size: 3rem;
    }
    [role='alert'] {
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
      background: var(--color-danger-soft);
    }
  `,
})
export class EmailChangeConfirmPage {
  private readonly http = inject(HttpClient);

  readonly busy = signal(false);
  readonly state = signal<'ready' | 'done' | 'invalid' | 'error'>('ready');
  private readonly token: string;

  constructor() {
    this.token = typeof window !== 'undefined' ? window.location.hash.replace(/^#/, '') : '';
    if (!this.token) {
      this.state.set('invalid');
    }
  }

  confirm(): void {
    this.busy.set(true);
    this.http.post('/api/v1/auth/email-change/confirm', { token: this.token }).subscribe({
      next: () => {
        this.busy.set(false);
        this.state.set('done');
      },
      error: (err: { status?: number }) => {
        this.busy.set(false);
        // 404 is the one answer for every unusable link; anything else (busy, rate limit, network) can be retried.
        this.state.set(err.status === 404 ? 'invalid' : 'error');
      },
    });
  }
}
