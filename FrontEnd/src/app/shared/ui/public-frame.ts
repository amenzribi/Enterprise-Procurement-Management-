import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { BrandMark } from './brand-mark';
import { LanguageSelect } from './language-select';
import { ThemeToggle } from './theme-toggle';

/**
 * Frame for pages reached without the signed-in workspace (password links, supplier quote links): the brand bar with
 * the language switch, then the page's content in one centred card. The page keeps its own `<main>` and heading.
 */
@Component({
  selector: 'app-public-frame',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMark, LanguageSelect, ThemeToggle, TranslatePipe],
  template: `
    <header class="bar">
      <span class="brand">
        <app-brand-mark [size]="28" />
        <span>{{ 'app.name' | translate }}</span>
      </span>
      <span class="tools">
        <app-language-select />
        <app-theme-toggle />
      </span>
    </header>
    <div class="body">
      <div class="card"><ng-content /></div>
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-block-size: 100dvh;
      background: var(--color-paper);
    }

    .bar {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: var(--space-3);
      min-block-size: var(--header-height);
      padding: var(--space-2) clamp(var(--space-4), 4vw, var(--space-10));
      border-block-end: 1px solid var(--color-ink-line);
      background: var(--color-ink);
      color: var(--color-on-ink);
    }

    .bar :focus-visible {
      outline-color: var(--color-brand);
    }

    .brand {
      display: inline-flex;
      align-items: center;
      gap: var(--space-3);
      font-family: var(--font-display);
      font-size: 1.0625rem;
      font-weight: 600;
    }

    .tools {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
    }

    .body {
      flex: 1;
      display: grid;
      align-content: start;
      justify-items: center;
      padding: clamp(var(--space-5), 6vh, var(--space-12)) var(--space-4) var(--space-10);
    }

    .card {
      inline-size: min(var(--frame-width, 30rem), 100%);
      padding: var(--space-8);
      border: 1px solid var(--color-line);
      border-radius: var(--radius-xl);
      background: var(--color-surface);
      box-shadow: var(--shadow-raised);
    }

    @media (max-width: 30rem) {
      .card {
        padding: var(--space-5);
        border-radius: var(--radius-lg);
      }
    }
  `,
})
export class PublicFrame {}
