import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Placeholder block shown while content loads. Reserve the final size to avoid layout shift; announce the
 * loading state separately (e.g. a visually hidden live region), as skeletons are hidden from assistive tech.
 */
@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.inline-size]': 'width()',
    '[style.block-size]': 'height()',
    '[class.round]': 'shape() === "circle"',
  },
  template: '',
  styles: `
    :host {
      display: block;
      border-radius: var(--radius-md);
      background: linear-gradient(
        90deg,
        var(--color-surface-sunken) 0%,
        var(--color-line) 50%,
        var(--color-surface-sunken) 100%
      );
      background-size: 200% 100%;
      animation: shimmer 1.4s ease-in-out infinite;
    }

    :host(.round) {
      border-radius: 50%;
    }

    @keyframes shimmer {
      from { background-position: 100% 0; }
      to { background-position: -100% 0; }
    }

    @media (prefers-reduced-motion: reduce) {
      :host {
        animation: none;
      }
    }
  `,
})
export class Skeleton {
  readonly width = input('100%');
  readonly height = input('1rem');
  readonly shape = input<'block' | 'circle'>('block');
}
