import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Icon } from '../../shared/ui/icon';
import { NotificationService } from './notification.service';

/** Renders notifications in a polite live region at the bottom of the screen. */
@Component({
  selector: 'app-toaster',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, TranslatePipe],
  template: `
    <div class="region" role="status" aria-live="polite">
      @for (item of notifications.notifications(); track item.id) {
        <div [class]="'toast toast--' + item.kind">
          <app-icon [name]="item.kind === 'error' ? 'triangle-alert' : 'circle-check'" [size]="18" class="toast-icon" />
          <p class="toast-text">{{ item.key | translate: item.params }}</p>
          <button
            type="button"
            class="toast-close"
            [attr.aria-label]="'common.dismiss' | translate"
            (click)="notifications.dismiss(item.id)"
          >
            <app-icon name="x" [size]="16" />
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .region {
      position: fixed;
      inset-block-end: var(--space-6);
      inset-inline-end: var(--space-6);
      z-index: 200;
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      inline-size: min(24rem, calc(100vw - 2 * var(--space-4)));
      pointer-events: none;
    }

    .toast {
      display: flex;
      align-items: flex-start;
      gap: var(--space-3);
      padding: var(--space-3) var(--space-3) var(--space-3) var(--space-4);
      border-radius: var(--radius-md);
      background: var(--color-nav);
      color: var(--color-nav-text-strong);
      box-shadow: var(--shadow-overlay);
      pointer-events: auto;
    }

    .toast-icon {
      margin-block-start: 0.125rem;
      color: var(--color-accent-on-dark);
    }

    .toast--error .toast-icon {
      color: #ff9b8f;
    }

    .toast-text {
      flex: 1;
      font-size: var(--text-base);
    }

    .toast-close {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-inline-size: 2rem;
      min-block-size: 2rem;
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      color: var(--color-nav-text);
      cursor: pointer;
    }

    .toast-close:hover {
      background: var(--color-nav-raised);
      color: var(--color-nav-text-strong);
    }

    .toast-close:focus-visible {
      outline-color: var(--color-accent-on-dark);
    }

    @media (max-width: 30rem) {
      .region {
        inset-inline: var(--space-4);
        inset-block-end: var(--space-4);
        inline-size: auto;
      }
    }
  `,
})
export class Toaster {
  protected readonly notifications = inject(NotificationService);
}
