import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost';

/**
 * Styles a native `<button>` or `<a>` so semantics, keyboard behaviour and form submission stay native.
 * Usage: `<button appButton variant="secondary">Retry</button>`.
 */
@Component({
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'button[appButton], a[appButton]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'app-button',
    '[class.app-button--primary]': "variant() === 'primary'",
    '[class.app-button--secondary]': "variant() === 'secondary'",
    '[class.app-button--ghost]': "variant() === 'ghost'",
    '[class.app-button--small]': "size() === 'small'",
    '[class.is-busy]': 'busy()',
    '[attr.aria-busy]': "busy() ? 'true' : null",
  },
  template: '<ng-content />',
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--space-2);
      min-block-size: 2.5rem;
      padding-inline: var(--space-4);
      border: 1px solid transparent;
      border-radius: var(--radius-sm);
      font-size: var(--text-base);
      font-weight: var(--weight-semibold);
      line-height: 1;
      text-decoration: none;
      white-space: nowrap;
      cursor: pointer;
      transition:
        background-color var(--duration-fast) var(--ease-standard),
        border-color var(--duration-fast) var(--ease-standard),
        box-shadow var(--duration-fast) var(--ease-standard),
        color var(--duration-fast) var(--ease-standard),
        transform var(--duration-fast) var(--ease-standard);
    }

    // A press is acknowledged at once, before the server answers.
    :host(:active) {
      transform: scale(0.98);
    }

    :host(.app-button--small) {
      min-block-size: 2rem;
      padding-inline: var(--space-3);
      font-size: var(--text-sm);
    }

    :host(.app-button--primary) {
      background: var(--color-accent);
      color: var(--color-text-inverse);
      box-shadow: var(--shadow-button);
    }

    :host(.app-button--primary:hover) {
      background: var(--color-accent-hover);
      color: var(--color-text-inverse);
    }

    :host(.app-button--secondary) {
      background: var(--color-surface);
      border-color: var(--color-line-strong);
      color: var(--color-text);
    }

    :host(.app-button--secondary:hover) {
      border-color: var(--color-accent);
      background: var(--color-accent-soft);
      color: var(--color-accent-hover);
    }

    :host(.app-button--ghost) {
      background: transparent;
      color: var(--color-text);
    }

    :host(.app-button--ghost:hover) {
      background: var(--color-surface-sunken);
      color: var(--color-text);
    }

    /* Busy: the brand ring turns in front of the label. Tied to a real request, never shown for effect. */
    :host(.is-busy)::before {
      content: '';
      flex-shrink: 0;
      inline-size: 1rem;
      block-size: 1rem;
      border: 2px solid currentColor;
      border-inline-end-color: transparent;
      border-radius: 50%;
      animation: button-ring 700ms linear infinite;
    }

    @keyframes button-ring {
      to {
        transform: rotate(360deg);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host(.is-busy)::before {
        animation: none;
        border-inline-end-color: currentColor;
        opacity: 0.6;
      }
    }

    :host([disabled]),
    :host([aria-disabled='true']) {
      opacity: 0.5;
      box-shadow: none;
      cursor: not-allowed;
      pointer-events: none;
    }
  `,
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<'default' | 'small'>('default');
  /** Shows the ring while the action this button started is in flight. The caller also disables it. */
  readonly busy = input(false);
}
