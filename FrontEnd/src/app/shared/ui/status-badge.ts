import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

/**
 * Compact status label: a soft pill. Meaning is carried by the text; the colour and dot only reinforce it.
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
      gap: 0.375rem;
      padding-block: 0.125rem;
      padding-inline: 0.5rem 0.625rem;
      border-radius: var(--radius-pill);
      background: var(--badge-soft);
      color: var(--badge-strong);
      font-size: var(--text-xs);
      font-weight: var(--weight-semibold);
      line-height: 1.5;
      white-space: nowrap;
    }

    .dot {
      inline-size: 0.375rem;
      block-size: 0.375rem;
      border-radius: 50%;
      background: var(--badge-strong);
      flex-shrink: 0;
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
