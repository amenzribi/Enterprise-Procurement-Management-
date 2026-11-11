import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Icon, IconName } from './icon';

/**
 * Shown when there is nothing to display yet. Says what is missing and offers the next action (projected).
 */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <app-icon class="icon" [name]="icon()" [size]="28" />
    <h2 class="title">{{ heading() }}</h2>
    @if (message()) {
      <p class="message">{{ message() }}</p>
    }
    <div class="actions"><ng-content /></div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: var(--space-2);
      padding: var(--space-8) var(--space-6);
      border: 1px dashed var(--color-line-strong);
      border-radius: var(--radius-lg);
      background: color-mix(in srgb, var(--color-surface) 60%, transparent);
    }

    .icon {
      display: grid;
      place-items: center;
      inline-size: 2.75rem;
      block-size: 2.75rem;
      margin-block-end: var(--space-2);
      border-radius: var(--radius-md);
      background: var(--color-accent-soft);
      color: var(--color-accent);
    }

    .title {
      font-size: var(--text-md);
    }

    .message {
      max-inline-size: 60ch;
      color: var(--color-text-muted);
    }

    .actions {
      margin-block-start: var(--space-3);
    }

    .actions:empty {
      display: none;
    }
  `,
})
export class EmptyState {
  readonly heading = input.required<string>();
  readonly message = input<string>();
  readonly icon = input<IconName>('inbox');
}
