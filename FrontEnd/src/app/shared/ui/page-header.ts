import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Page title and one-line purpose, with an optional slot for page-level actions.
 */
@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="text">
      <h1 tabindex="-1" class="title">{{ heading() }}</h1>
      @if (description()) {
        <p class="description">{{ description() }}</p>
      }
    </div>
    <div class="actions"><ng-content /></div>
  `,
  styles: `
    :host {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--space-4) var(--space-6);
      margin-block-end: var(--space-6);
    }

    .text {
      min-inline-size: 0;
    }

    .title {
      font-size: var(--text-xl);
      overflow-wrap: anywhere;
    }

    @media (max-width: 40rem) {
      .title {
        font-size: 1.5rem;
      }
    }

    .title:focus {
      outline: none;
    }

    .description {
      margin-block-start: var(--space-1);
      max-inline-size: 68ch;
      color: var(--color-text-muted);
      font-size: var(--text-base);
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
    }

    .actions:empty {
      display: none;
    }
  `,
})
export class PageHeader {
  readonly heading = input.required<string>();
  readonly description = input<string>();
}
