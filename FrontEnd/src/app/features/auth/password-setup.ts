import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { PageHeader } from '../../shared/ui/page-header';

@Component({
  selector: 'app-password-setup',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, PageHeader, ReactiveFormsModule, RouterLink, TranslatePipe],
  template: `
    <main class="wrap">
      <app-page-header [heading]="'passwordSetup.title' | translate" [description]="'passwordSetup.description' | translate" />
      @if (invalid()) {
        <p role="alert">{{ 'passwordSetup.invalid' | translate }}</p>
        <a routerLink="/sign-in">{{ 'auth.signIn.title' | translate }}</a>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()">
          <label
            >{{ 'passwordSetup.newPassword' | translate }}
            <input type="password" formControlName="password" autocomplete="new-password" />
          </label>
          <label
            >{{ 'passwordSetup.confirm' | translate }}
            <input type="password" formControlName="confirm" autocomplete="new-password" />
          </label>
          <button appButton type="submit" [disabled]="form.invalid || busy()">
            {{ 'passwordSetup.submit' | translate }}
          </button>
        </form>
      }
    </main>
  `,
  styles: `
    .wrap {
      max-inline-size: 28rem;
      margin-inline: auto;
      padding: 1.5rem;
      display: grid;
      gap: 1rem;
    }
    form {
      display: grid;
      gap: 0.75rem;
    }
    label {
      display: grid;
      gap: 0.25rem;
    }
    input {
      min-block-size: 2.4rem;
      padding-inline: 0.6rem;
    }
  `,
})
export class PasswordSetupPage {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly busy = signal(false);
  readonly invalid = signal(false);
  private token = '';

  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(12)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    const fragment = typeof window !== 'undefined' ? window.location.hash.replace(/^#/, '') : '';
    this.token = fragment;
    if (!fragment) {
      this.invalid.set(true);
    }
  }

  submit(): void {
    const { password, confirm } = this.form.getRawValue();
    if (password !== confirm) {
      this.notifications.error('passwordSetup.mismatch');
      return;
    }
    this.busy.set(true);
    this.http.post('/api/v1/auth/password-reset/redeem', { token: this.token, newPassword: password }).subscribe({
      next: () => {
        this.busy.set(false);
        this.notifications.success('passwordSetup.done');
        void this.router.navigateByUrl('/sign-in');
      },
      error: (err) => {
        this.busy.set(false);
        this.invalid.set(true);
        this.notifications.error(`errors.${toApiProblem(err).code}`);
      },
    });
  }
}
