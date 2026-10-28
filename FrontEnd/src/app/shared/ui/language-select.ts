import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { Language, LanguageService, SUPPORTED_LANGUAGES } from '../../core/i18n/language.service';
import { Icon } from './icon';

/**
 * Language switcher (العربية / Français / English). Each language is labelled in its own language so users can always
 * find theirs.
 */
@Component({
  selector: 'app-language-select',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, TranslatePipe],
  template: `
    <div class="switcher" role="group" [attr.aria-label]="'language.label' | translate">
      <app-icon name="globe" [size]="16" class="globe" />
      @for (language of languages; track language) {
        <button
          type="button"
          class="option"
          [attr.lang]="language"
          [attr.aria-pressed]="language === languageService.current()"
          (click)="select(language)"
        >
          <span aria-hidden="true">{{ shortLabels[language] }}</span>
          <span class="visually-hidden">{{ 'language.names.' + language | translate }}</span>
        </button>
      }
    </div>
    <p class="switch-error" role="status">
      @if (languageService.switchFailure(); as failed) {
        {{ 'language.loadFailed' | translate: { language: ('language.names.' + failed | translate) } }}
      }
    </p>
  `,
  host: { class: 'language-select' },
  styles: `
    :host {
      position: relative;
      display: inline-block;
    }

    .switcher {
      display: inline-flex;
      align-items: center;
      gap: var(--space-1);
      padding: 0.1875rem;
      border: 1px solid var(--color-line);
      border-radius: var(--radius-md);
      background: var(--color-surface);
    }

    .globe {
      color: var(--color-text-muted);
      padding-inline: var(--space-1);
    }

    .option {
      min-inline-size: 2.75rem;
      min-block-size: 2rem;
      padding-inline: var(--space-2);
      border: 0;
      border-radius: var(--radius-sm);
      background: transparent;
      color: var(--color-text-muted);
      font-size: var(--text-sm);
      font-weight: var(--weight-medium);
      cursor: pointer;
      transition: background-color var(--duration-fast) var(--ease-standard);
    }

    .option:hover {
      color: var(--color-text);
      background: var(--color-surface-sunken);
    }

    .option[aria-pressed='true'] {
      background: var(--color-accent);
      color: var(--color-text-inverse);
    }

    @media (max-width: 29.99rem) {
      .globe {
        display: none;
      }

      .option {
        min-inline-size: 2.25rem;
        padding-inline: var(--space-1);
      }
    }

    .switch-error {
      position: absolute;
      inset-block-start: calc(100% + var(--space-1));
      inset-inline-end: 0;
      margin: 0;
      inline-size: max-content;
      max-inline-size: 16rem;
      color: var(--color-danger);
      font-size: var(--text-xs);
    }

    .switch-error:empty {
      display: none;
    }
  `,
})
export class LanguageSelect {
  protected readonly languageService = inject(LanguageService);
  protected readonly languages = SUPPORTED_LANGUAGES;
  /** Visible short labels, each written in its own language; the full name is announced to screen readers. */
  protected readonly shortLabels: Record<Language, string> = { ar: 'عربي', fr: 'FR', en: 'EN' };

  protected select(language: Language): void {
    if (language !== this.languageService.current()) {
      this.languageService.use(language).subscribe();
    }
  }
}
