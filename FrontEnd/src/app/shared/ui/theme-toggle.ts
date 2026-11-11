import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ThemeService } from '../../core/theme/theme.service';
import { Icon } from './icon';

/**
 * Switches between the light and dark appearance. The button names the theme it switches to, and the icon shows it.
 */
@Component({
  selector: 'app-theme-toggle',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, TranslatePipe],
  template: `
    <button
      type="button"
      class="toggle"
      [attr.aria-label]="(theme.current() === 'dark' ? 'theme.toLight' : 'theme.toDark') | translate"
      [attr.title]="(theme.current() === 'dark' ? 'theme.toLight' : 'theme.toDark') | translate"
      (click)="theme.toggle()"
    >
      <app-icon [name]="theme.current() === 'dark' ? 'sun' : 'moon'" [size]="18" />
    </button>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    .toggle {
      display: grid;
      place-items: center;
      inline-size: 2.5rem;
      block-size: 2.5rem;
      padding: 0;
      border: 1px solid var(--color-line);
      border-radius: 50%;
      background: var(--color-surface);
      color: var(--color-text-muted);
      cursor: pointer;
      transition:
        background-color var(--duration-fast) var(--ease-standard),
        color var(--duration-fast) var(--ease-standard),
        transform var(--duration-fast) var(--ease-standard);
    }

    .toggle:hover {
      background: var(--color-surface-sunken);
      color: var(--color-text);
    }

    .toggle:active {
      transform: scale(0.94);
    }
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
}
