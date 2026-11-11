import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * The Procurement 360 mark: an open ring (the full circle a request travels, from need to closure) and the point
 * that travels it. Decorative: the product name is always written next to it or announced by the page.
 */
@Component({
  selector: 'app-brand-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', '[style.inline-size.px]': 'size()', '[style.block-size.px]': 'size()' },
  template: `
    <svg viewBox="0 0 32 32" fill="none" focusable="false">
      <circle class="ring" cx="16" cy="16" r="11" stroke-width="3.5" stroke-linecap="round" pathLength="100" stroke-dasharray="78 22" />
      <circle class="point" cx="16" cy="5" r="3.25" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-block;
      flex-shrink: 0;
      color: var(--color-on-ink);
    }

    svg {
      display: block;
      inline-size: 100%;
      block-size: 100%;
    }

    .ring {
      stroke: currentColor;
      transform: rotate(-52deg);
      transform-origin: 16px 16px;
    }

    .point {
      fill: var(--color-brand);
    }
  `,
})
export class BrandMark {
  readonly size = input(32);
}
