import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'epi.theme';

/**
 * Light or dark appearance. A choice made with the switch is remembered on this device; until one is made the
 * system setting decides and is followed live. The theme is applied as `data-theme` on the document element, which
 * is what the design tokens key on.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly systemQuery = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)') ?? null;

  private readonly chosen = signal<Theme | null>(this.readChoice());
  private readonly system = signal<Theme>(this.systemQuery?.matches ? 'dark' : 'light');

  readonly current = computed<Theme>(() => this.chosen() ?? this.system());

  constructor() {
    this.systemQuery?.addEventListener?.('change', (event) => {
      this.system.set(event.matches ? 'dark' : 'light');
      this.apply();
    });
    this.apply();
  }

  toggle(): void {
    this.set(this.current() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.chosen.set(theme);
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // The choice is kept for this visit only.
    }
    this.apply();
  }

  private apply(): void {
    this.document.documentElement.setAttribute('data-theme', this.current());
  }

  private readChoice(): Theme | null {
    try {
      const stored = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
      return stored === 'light' || stored === 'dark' ? stored : null;
    } catch {
      return null;
    }
  }
}
