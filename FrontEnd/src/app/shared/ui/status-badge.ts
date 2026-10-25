import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/**
 * Compact status label. Meaning is carried by the text; the colour and dot only reinforce it.
 */
@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': "'tone-' + tone()" },
  template: `<span class="dot" aria-hidden="true"></span><ng-content />`,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      padding: 0.125rem var(--space-2);
      border-radius: var(--radius-pill);
      font-size: var(--text-xs);
      font-weight: var(--weight-medium);
      line-height: 1.5;
      white-space: nowrap;
      background: var(--badge-soft);
      color: var(--badge-strong);
    }

    .dot {
      inline-size: 0.375rem;
      block-size: 0.375rem;
      border-radius: 50%;
      background: currentColor;
    }

    :host(.tone-success) { --badge-soft: var(--color-success-soft); --badge-strong: var(--color-success); }
    :host(.tone-warning) { --badge-soft: var(--color-warning-soft); --badge-strong: var(--color-warning); }
    :host(.tone-danger) { --badge-soft: var(--color-danger-soft); --badge-strong: var(--color-danger); }
    :host(.tone-info) { --badge-soft: var(--color-info-soft); --badge-strong: var(--color-info); }
    :host(.tone-neutral) { --badge-soft: var(--color-neutral-soft); --badge-strong: var(--color-neutral); }
  `,
})
export class StatusBadge {
  readonly tone = input<StatusTone>('neutral');
}
