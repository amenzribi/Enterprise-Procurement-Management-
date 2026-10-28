import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subject, catchError, map, of, tap } from 'rxjs';

export const SUPPORTED_LANGUAGES = ['ar', 'fr', 'en'] as const;
export type Language = (typeof SUPPORTED_LANGUAGES)[number];

export const DEFAULT_LANGUAGE: Language = 'en';
const STORAGE_KEY = 'epi.language';

/** Right-to-left languages. The document direction follows the current language (S2-AC-01). */
const RTL_LANGUAGES: readonly Language[] = ['ar'];

export function isRtl(language: Language): boolean {
  return RTL_LANGUAGES.includes(language);
}

/**
 * Locale used for dates, numbers and relative times. Arabic uses ar-TN: Arabic texts with Latin digits, as used in
 * Maghreb business documents. English uses en-GB (day/month order).
 */
export function localeOf(language: string): string {
  switch (language) {
    case 'ar':
      return 'ar-TN';
    case 'fr':
      return 'fr-FR';
    default:
      return 'en-GB';
  }
}

export function isSupportedLanguage(value: unknown): value is Language {
  return typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);
}

/**
 * Owns the user's display language: initial choice (saved preference, then browser language, then English),
 * switching at runtime without reload, persistence, and the document `lang` and `dir` attributes.
 *
 * A language only becomes current once its texts have loaded, so the selector, `<html lang>` and the visible
 * texts never disagree. Start-up never fails because of translations: it falls back to English, then to keys.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  private readonly currentLanguage = signal<Language>(DEFAULT_LANGUAGE);
  readonly current = this.currentLanguage.asReadonly();
  /** Locale of the current language for DatePipe, Intl and money formatting. */
  readonly locale = computed(() => localeOf(this.currentLanguage()));
  readonly rtl = computed(() => isRtl(this.currentLanguage()));

  private readonly failedLanguage = signal<Language | null>(null);
  /** The language whose texts last failed to load during a switch, until the next successful switch. */
  readonly switchFailure = this.failedLanguage.asReadonly();

  private readonly userSelections = new Subject<Language>();
  /**
   * Successful switches made by the user (not those applied from the account preference), so the account can
   * store them (S1-AC-12).
   */
  readonly selections = this.userSelections.asObservable();

  init(): Observable<boolean> {
    const preferred = this.resolveInitialLanguage();
    return this.load(preferred).pipe(
      catchError(() => (preferred === DEFAULT_LANGUAGE ? of(false) : this.load(DEFAULT_LANGUAGE))),
      catchError(() => of(false)),
      map((loaded) => {
        if (!loaded) {
          // No texts could be loaded: render keys rather than a blank page.
          this.commit(DEFAULT_LANGUAGE);
        }
        return loaded;
      }),
    );
  }

  /**
   * Emits true when the switch succeeded; on failure the current language is kept and `switchFailure` is set.
   * `origin` tells whether the user chose the language or it was applied from their account.
   */
  use(language: Language, origin: 'user' | 'account' = 'user'): Observable<boolean> {
    return this.load(language).pipe(
      tap(() => {
        this.store(language);
        this.failedLanguage.set(null);
        if (origin === 'user') {
          this.userSelections.next(language);
        }
      }),
      catchError(() => {
        this.failedLanguage.set(language);
        return of(false);
      }),
    );
  }

  private load(language: Language): Observable<true> {
    return this.translate.use(language).pipe(
      tap(() => this.commit(language)),
      map(() => true as const),
    );
  }

  private commit(language: Language): void {
    this.currentLanguage.set(language);
    this.document.documentElement.lang = language;
    this.document.documentElement.dir = isRtl(language) ? 'rtl' : 'ltr';
  }

  private resolveInitialLanguage(): Language {
    const stored = this.read();
    if (isSupportedLanguage(stored)) {
      return stored;
    }
    const browser = this.document.defaultView?.navigator?.language?.slice(0, 2).toLowerCase();
    return isSupportedLanguage(browser) ? browser : DEFAULT_LANGUAGE;
  }

  // Storage can be unavailable (private mode, blocked site data); the preference is then kept for the session only.
  private read(): string | null {
    try {
      return this.document.defaultView?.localStorage.getItem(STORAGE_KEY) ?? null;
    } catch {
      return null;
    }
  }

  private store(language: Language): void {
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, language);
    } catch {
      // Preference cannot be persisted; the current session still switches language.
    }
  }
}
