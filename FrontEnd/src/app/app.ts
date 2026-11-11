import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { SessionWarning } from './core/auth/session-warning';
import { Toaster } from './core/notifications/toaster';
import { ThemeService } from './core/theme/theme.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, SessionWarning, Toaster],
  template: `
    <router-outlet />
    <app-toaster />
    @if (auth.isAuthenticated()) {
      <app-session-warning />
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  /** Created with the application so the saved or system theme applies before the first screen. */
  protected readonly theme = inject(ThemeService);
}
