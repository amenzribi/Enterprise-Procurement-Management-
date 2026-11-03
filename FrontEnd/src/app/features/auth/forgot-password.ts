import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { LanguageSelect } from '../../shared/ui/language-select';

/**
 * Public forgot-password. The server always returns the same outcome so account existence is not revealed.
 */
@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, LanguageSelect, ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="page">
      <main class="form-side" id="main" tabindex="-1">
        <div class="form-top"><app-language-select /></div>
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
    </div>
  `,
  styles: `
    .page {
      min-block-size: 100dvh;
      display: grid;
      place-items: center;
      padding: var(--space-4);
      background: var(--color-canvas, var(--color-surface));
    }
    .form-wrap {
      inline-size: min(24rem, 100%);
      display: grid;
      gap: var(--space-4);
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
      min-block-size: 2.5rem;
      padding-inline: var(--space-3);
    }
    .notice {
      display: flex;
      gap: var(--space-2);
      align-items: start;
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
