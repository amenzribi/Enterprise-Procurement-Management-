import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Button } from '../../shared/ui/button';
import { AuthService } from './auth.service';
import { SessionTimerService } from './session-timer.service';

/** Content of the expiry warning: countdown, "Stay signed in" and "Sign out". */
@Component({
  selector: 'app-session-warning-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, TranslatePipe],
  template: `
    <h2 id="session-warning-title" class="title">{{ 'auth.sessionWarning.title' | translate }}</h2>
    <p id="session-warning-message" class="message">
      {{ 'auth.sessionWarning.message' | translate: { minutes: minutes(), seconds: seconds() } }}
    </p>
    <div class="actions">
      <button appButton variant="secondary" type="button" (click)="signOut()">{{ 'auth.signOut' | translate }}</button>
      <button appButton type="button" cdkFocusInitial (click)="stay()">{{ 'auth.sessionWarning.stay' | translate }}</button>
    </div>
  `,
  styles: `
    :host {
      display: block;
      inline-size: min(28rem, calc(100vw - 2rem));
      padding: var(--space-6);
      border-radius: var(--radius-lg);
      background: var(--color-surface);
      box-shadow: var(--shadow-overlay);
    }

    .title {
      font-size: var(--text-lg);
    }

    .message {
      margin-block-start: var(--space-3);
      color: var(--color-text-muted);
      font-size: var(--text-md);
      font-variant-numeric: tabular-nums;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--space-2);
      margin-block-start: var(--space-6);
    }
  `,
})
export class SessionWarningDialog {
  private readonly auth = inject(AuthService);
  private readonly timer = inject(SessionTimerService);
  private readonly ref = inject(DialogRef);

  protected minutes(): number {
    return Math.floor(this.timer.secondsLeft() / 60);
  }

  protected seconds(): string {
    return String(this.timer.secondsLeft() % 60).padStart(2, '0');
  }

  protected stay(): void {
    // A real request extends the server session; the response resets the timer through the interceptor.
    this.auth.refresh().subscribe();
    this.ref.close();
  }

  protected signOut(): void {
    this.ref.close();
    this.auth.signOut().subscribe();
  }
}

/** Opens and closes the expiry warning as the session timer requires. Renders nothing itself. */
@Component({
  selector: 'app-session-warning',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class SessionWarning {
  private readonly dialog = inject(Dialog);
  private readonly timer = inject(SessionTimerService);
  private ref: DialogRef<unknown, SessionWarningDialog> | null = null;

  constructor() {
    effect(() => {
      const warn = this.timer.warning();
      if (warn && !this.ref) {
        this.ref = this.dialog.open(SessionWarningDialog, {
          role: 'alertdialog',
          ariaLabelledBy: 'session-warning-title',
          ariaDescribedBy: 'session-warning-message',
          autoFocus: '[cdkFocusInitial]',
          restoreFocus: true,
          disableClose: true,
        });
        this.ref.closed.subscribe(() => (this.ref = null));
      } else if (!warn && this.ref) {
        this.ref.close();
      }
    });
    inject(DestroyRef).onDestroy(() => this.ref?.close());
  }
}
