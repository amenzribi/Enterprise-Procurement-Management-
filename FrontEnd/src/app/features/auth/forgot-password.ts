import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { PublicFrame } from '../../shared/ui/public-frame';

/**
 * Public forgot-password. The server always returns the same outcome so account existence is not revealed.
 */
@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, PublicFrame, ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <app-public-frame>
      <main class="form-side" id="main" tabindex="-1">
        <div class="form-wrap">
          <h1 class="title" tabindex="-1">{{ 'auth.forgot.title' | translate }}</h1>
          <p class="lead">{{ 'auth.forgot.lead' | translate }}</p>
          @if (done()) {
            <div class="notice" role="status">
              <app-icon name="circle-check" [size]="18" />
              <p>{{ 'auth.forgot.done' | translate }}</p>
            </div>
            <a appButton routerLink="/sign-in">{{ 'auth.forgot.back' | translate }}</a>
          } @else {
            <form class="form" [formGroup]="form" (ngSubmit)="submit()" novalidate>
              <label for="forgot-email">{{ 'auth.signIn.email' | translate }}</label>
              <input id="forgot-email" type="email" formControlName="email" autocomplete="username" />
              <button appButton type="submit" [disabled]="busy() || form.invalid">
                {{ (busy() ? 'auth.forgot.submitting' : 'auth.forgot.submit') | translate }}
              </button>
            </form>
            <p class="back"><a routerLink="/sign-in">{{ 'auth.forgot.back' | translate }}</a></p>
          }
        </div>
      </main>
    </app-public-frame>
  `,
  styles: `
    .form-side:focus {
      outline: none;
    }
    .form-wrap {
      display: grid;
      gap: var(--space-4);
    }
    label {
      font-size: var(--text-sm);
      font-weight: var(--weight-semibold);
    }
    .title {
      margin: 0;
    }
    .lead,
    .back {
      color: var(--color-text-muted);
    }
    .back a {
      display: inline-flex;
      align-items: center;
      min-block-size: 2.75rem;
      padding-block: var(--space-2);
    }
    .form {
      display: grid;
      gap: var(--space-3);
    }
    input {
      min-block-size: 3rem;
      padding-inline: var(--space-4);
      font-size: var(--text-md);
    }
    .form button {
      min-block-size: 3rem;
    }
    .notice {
      display: flex;
      gap: var(--space-2);
      align-items: start;
      padding: var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
      background: var(--color-success-soft);
      color: var(--color-success);
    }
    .notice p {
      color: var(--color-text);
    }
  `,
})
export class ForgotPasswordPage {
  private readonly http = inject(HttpClient);
  protected readonly busy = signal(false);
  protected readonly done = signal(false);
  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
  });

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.http.post('/api/v1/auth/password-reset/request', { email: this.form.controls.email.value.trim() }).subscribe({
      next: () => {
        this.busy.set(false);
        this.done.set(true);
      },
      error: () => {
        // Same UX whether the account exists or the request failed at the edge — no account oracle.
        this.busy.set(false);
        this.done.set(true);
      },
    });
  }
}
