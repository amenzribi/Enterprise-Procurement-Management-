import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StageRingTone = 'active' | 'done' | 'stopped' | 'idle';

/**
 * How far a request has travelled around its lifecycle, drawn as a segmented ring: one segment per step, filled up
 * to the current one. It reinforces a status that is always written next to it, so it is hidden from assistive
 * technology.
 */
@Component({
  selector: 'app-stage-ring',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[class]': "'tone-' + tone()",
    '[style.inline-size.px]': 'size()',
    '[style.block-size.px]': 'size()',
    '[style.font-size.px]': 'size()',
  },
  template: `
    <svg viewBox="0 0 36 36" fill="none" focusable="false">
      <circle class="track" cx="18" cy="18" r="14" [attr.stroke-width]="stroke()" [attr.pathLength]="total() * 10" stroke-dasharray="8 2" />
      <circle class="progress" cx="18" cy="18" r="14" [attr.stroke-width]="stroke()" [attr.pathLength]="total() * 10" [attr.stroke-dasharray]="dashes()" />
    </svg>
    @if (showCount()) {
      <span class="count tabular">{{ filled() }}</span>
    }
  `,
  styles: `
    :host {
      position: relative;
      display: inline-grid;
      place-items: center;
      flex-shrink: 0;
      --ring-color: var(--color-accent);
    }

    :host(.tone-done) {
      --ring-color: var(--color-success);
    }

    :host(.tone-stopped) {
      --ring-color: var(--color-danger);
    }

    :host(.tone-idle) {
      --ring-color: var(--color-text-muted);
    }

    svg {
      display: block;
      inline-size: 100%;
      block-size: 100%;
      // The ring starts at twelve o'clock and fills in reading direction.
      transform: rotate(-90deg);
    }

    :host-context([dir='rtl']) svg {
      transform: scaleX(-1) rotate(-90deg);
    }

    .track {
      stroke: var(--color-line-strong);
      opacity: 0.6;
    }

    .progress {
      stroke: var(--ring-color);
    }

    .count {
      position: absolute;
      color: var(--color-text);
      font-family: var(--font-display);
      font-size: 0.36em;
      font-weight: 600;
      line-height: 1;
    }
  `,
})
export class StageRing {
  /** Zero-based position of the current step. */
  readonly step = input(0);
  readonly total = input(10);
  readonly tone = input<StageRingTone>('active');
  readonly size = input(28);
  /** Writes the step number in the centre (large rings only). */
  readonly showCount = input(false);

  protected readonly filled = computed(() => Math.min(this.total(), Math.max(0, this.step()) + 1));
  protected readonly stroke = computed(() => (this.size() >= 48 ? 3 : 4));
  protected readonly dashes = computed(() => {
    const filled = this.filled();
    const rest = (this.total() - filled) * 10;
    return `${Array.from({ length: filled }, () => '8 2').join(' ')} 0 ${rest}`;
  });
}
