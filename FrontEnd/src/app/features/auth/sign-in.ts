import { ChangeDetectionStrategy, Component, ElementRef, inject, isDevMode, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/safe-return-url';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { LanguageSelect } from '../../shared/ui/language-select';

type SignInError = 'invalid-credentials' | 'network-error' | 'unexpected-error' | null;

/**
 * Sign-in with e-mail and password. Every failure shows the same message so the page never reveals whether an
 * account exists or is throttled (S1DR-03).
 */
@Component({
  selector: 'app-sign-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon, LanguageSelect, ReactiveFormsModule, TranslatePipe],
  templateUrl: './sign-in.html',
  styleUrl: './sign-in.scss',
})
export class SignInPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly isDevMode = isDevMode();
  protected readonly reason = this.route.snapshot.queryParamMap.get('reason');
  private readonly returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected readonly submitting = signal(false);
  protected readonly error = signal<SignInError>(null);
  protected readonly showPassword = signal(false);
  protected readonly submitted = signal(false);

  private readonly emailInput = viewChild<ElementRef<HTMLInputElement>>('emailInput');
  private readonly passwordInput = viewChild<ElementRef<HTMLInputElement>>('passwordInput');

  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      (this.form.controls.email.invalid ? this.emailInput() : this.passwordInput())?.nativeElement.focus();
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.auth.signIn(email.trim(), password).subscribe({
      next: () => {
        this.submitting.set(false);
        void this.router.navigateByUrl(this.returnUrl);
      },
      error: (failure: unknown) => {
        this.submitting.set(false);
        const problem = toApiProblem(failure);
        this.error.set(
          problem.status === 0
            ? 'network-error'
            : problem.status === 401 || problem.status === 423 || problem.status === 429
              ? 'invalid-credentials'
              : 'unexpected-error',
        );
        this.form.controls.password.reset();
        this.passwordInput()?.nativeElement.focus();
      },
    });
  }

  protected fieldInvalid(name: 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }
}
