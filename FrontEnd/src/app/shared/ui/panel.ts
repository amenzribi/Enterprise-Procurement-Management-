import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Bordered content container with an optional title row. Use for a group of related information, not as
 * decoration around every element.
 */
@Component({
  selector: 'app-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (heading()) {
      <header class="panel-header">
        <h2 class="panel-title" [id]="headingId">{{ heading() }}</h2>
        <ng-content select="[panelActions]" />
      </header>
    }
    <div class="panel-body"><ng-content /></div>
  `,
  host: {
    '[attr.role]': "heading() ? 'region' : null",
    '[attr.aria-labelledby]': 'heading() ? headingId : null',
  },
  styles: `
    :host {
      display: block;
      background: var(--color-surface);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-lg);
      box-shadow: var(--shadow-card);
    }

    .panel-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      min-block-size: 3.5rem;
      padding: var(--space-3) var(--space-5);
      border-block-end: 1px solid var(--color-line);
    }

    .panel-title {
      font-size: var(--text-md);
    }

    .panel-body {
      padding: var(--space-5);
    }
  `,
})
export class Panel {
  private static nextId = 0;

  readonly heading = input<string>();
  protected readonly headingId = `panel-heading-${Panel.nextId++}`;
}
