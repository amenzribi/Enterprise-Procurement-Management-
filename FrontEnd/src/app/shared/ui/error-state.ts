import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from './button';
import { Icon } from './icon';

/**
 * Explains what failed and how to recover, with a retry action. Announced to assistive technology on appearance,
 * unless the page already announces the failure through its own live region (`announce` = false).
 */
@Component({
  selector: 'app-error-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, Icon],
  host: { '[attr.role]': "announce() ? 'alert' : null" },
  template: `
    <app-icon class="icon" name="triangle-alert" [size]="22" />
    <div class="body">
      <h2 class="title">{{ heading() }}</h2>
      @if (message()) {
        <p class="message">{{ message() }}</p>
      }
      @if (retryLabel()) {
        <button appButton variant="secondary" size="small" type="button" class="retry" (click)="retry.emit()">
          <app-icon name="refresh" [size]="16" />
          {{ retryLabel() }}
        </button>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      gap: var(--space-3);
      padding: var(--space-5);
      border: 1px solid color-mix(in srgb, var(--color-danger) 30%, transparent);
      border-inline-start: 4px solid var(--color-danger);
      border-radius: var(--radius-md);
      background: var(--color-danger-soft);
    }

    .icon {
      color: var(--color-danger);
      margin-block-start: 0.125rem;
    }

    .title {
      font-size: var(--text-md);
    }

    .message {
      margin-block-start: var(--space-1);
      max-inline-size: 65ch;
    }

    .retry {
      margin-block-start: var(--space-4);
    }
  `,
})
export class ErrorState {
  readonly heading = input.required<string>();
  readonly message = input<string>();
  readonly retryLabel = input<string>();
  readonly announce = input(true);
  readonly retry = output<void>();
}
