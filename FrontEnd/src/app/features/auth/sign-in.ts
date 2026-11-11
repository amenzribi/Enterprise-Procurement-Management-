import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, afterNextRender, inject, isDevMode, signal, viewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { toApiProblem } from '../../core/api/api-problem';
import { AuthService } from '../../core/auth/auth.service';
import { safeReturnUrl } from '../../core/auth/safe-return-url';
import { workspaceHome } from '../../core/auth/workspace-home';
import { BrandMark } from '../../shared/ui/brand-mark';
import { Button } from '../../shared/ui/button';
import { Icon } from '../../shared/ui/icon';
import { LanguageSelect } from '../../shared/ui/language-select';
import { ThemeToggle } from '../../shared/ui/theme-toggle';
import { REQUEST_STAGES } from '../requests/request-status';

type SignInError = 'invalid-credentials' | 'network-error' | 'unexpected-error' | null;

/**
 * Sign-in with e-mail and password. Every failure shows the same message so the page never reveals whether an
 * account exists or is throttled (S1DR-03).
 */
@Component({
  selector: 'app-sign-in',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMark, Button, Icon, LanguageSelect, ReactiveFormsModule, RouterLink, ThemeToggle, TranslatePipe],
  templateUrl: './sign-in.html',
  styleUrl: './sign-in.scss',
})
export class SignInPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly isDevMode = isDevMode();

  /** The lifecycle stages placed around the brand orbit, starting at twelve o'clock (percent of the orbit box). */
  protected readonly stations = REQUEST_STAGES.map((key, index) => {
    const angle = (index / REQUEST_STAGES.length) * 2 * Math.PI - Math.PI / 2;
    return {
      key,
      x: Math.round((50 + 46 * Math.cos(angle)) * 100) / 100,
      y: Math.round((50 + 42 * Math.sin(angle)) * 100) / 100,
      below: Math.sin(angle) > 0.05,
      // Stages at the far left and right carry their label outside the ring instead of across it.
      side: Math.cos(angle) > 0.75 ? 'right' : Math.cos(angle) < -0.75 ? 'left' : null,
    };
  });
  protected readonly reason = this.route.snapshot.queryParamMap.get('reason');

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

  constructor() {
    const host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    const destroyRef = inject(DestroyRef);
    // Depth: the layers of the stage follow the pointer by a few pixels. Mouse only, and never when motion is
    // reduced. The listener writes two CSS variables and does not touch Angular's change detection.
    afterNextRender(() => {
      const view = host.ownerDocument.defaultView;
      if (!view?.matchMedia || view.matchMedia('(prefers-reduced-motion: reduce)').matches || !view.matchMedia('(pointer: fine)').matches) {
        return;
      }
      const onMove = (event: PointerEvent): void => {
        host.style.setProperty('--mx', ((event.clientX / view.innerWidth) * 2 - 1).toFixed(3));
        host.style.setProperty('--my', ((event.clientY / view.innerHeight) * 2 - 1).toFixed(3));
      };
      view.addEventListener('pointermove', onMove, { passive: true });
      destroyRef.onDestroy(() => view.removeEventListener('pointermove', onMove));
    });
  }

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
        const destination = safeReturnUrl(
          this.route.snapshot.queryParamMap.get('returnUrl'),
          workspaceHome(this.auth.me()),
        );
        void this.router.navigateByUrl(destination);
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
