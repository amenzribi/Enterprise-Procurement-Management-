import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { Button } from '../../shared/ui/button';
import { PageHeader } from '../../shared/ui/page-header';

@Component({
  selector: 'app-change-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, PageHeader, ReactiveFormsModule, TranslatePipe],
  template: `
    <app-page-header
      [heading]="'changePassword.title' | translate"
      [description]="'changePassword.description' | translate"
    />
    <form class="form" [formGroup]="form" (ngSubmit)="submit()">
      <label
        >{{ 'changePassword.current' | translate }}
        <input type="password" formControlName="currentPassword" autocomplete="current-password" />
      </label>
      <label
        >{{ 'changePassword.next' | translate }}
        <input type="password" formControlName="newPassword" autocomplete="new-password" />
      </label>
      <label
        >{{ 'changePassword.confirm' | translate }}
        <input type="password" formControlName="confirm" autocomplete="new-password" />
      </label>
      <button appButton type="submit" [disabled]="form.invalid || busy()">
        {{ 'changePassword.submit' | translate }}
      </button>
    </form>
  `,
  styles: `
    .form {
      max-inline-size: 28rem;
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
export class ChangePasswordPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly busy = signal(false);

  readonly form = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(12)] }),
    confirm: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  submit(): void {
    const value = this.form.getRawValue();
    if (value.newPassword !== value.confirm) {
      this.notifications.error('changePassword.mismatch');
      return;
    }
    this.busy.set(true);
    this.http
      .post('/api/v1/auth/password/change', {
        currentPassword: value.currentPassword,
        newPassword: value.newPassword,
      })
      .subscribe({
        next: () => {
          this.busy.set(false);
          this.notifications.success('changePassword.done');
          this.auth.refresh().subscribe({
            next: () => void this.router.navigateByUrl('/requests'),
            error: () => void this.router.navigateByUrl('/requests'),
          });
        },
        error: (err) => {
          this.busy.set(false);
          this.notifications.error(`errors.${toApiProblem(err).code}`);
        },
      });
  }
}
