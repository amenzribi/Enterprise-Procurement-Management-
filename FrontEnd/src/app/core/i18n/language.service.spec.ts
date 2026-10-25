import { DOCUMENT } from '@angular/common';
import { Injectable } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  TranslateLoader,
  TranslateService,
  TranslationObject,
  provideTranslateLoader,
  provideTranslateService,
} from '@ngx-translate/core';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { LanguageService } from './language.service';

/** Serves a tiny dictionary per language and fails for the languages listed in `failing`. */
@Injectable()
class FakeLoader implements TranslateLoader {
  static failing = new Set<string>();

  getTranslation(lang: string): Observable<TranslationObject> {
    return FakeLoader.failing.has(lang) ? throwError(() => new Error(`no ${lang}`)) : of({ greeting: `hello-${lang}` });
  }
}

describe('LanguageService', () => {
  const STORAGE_KEY = 'epi.language';

  function setup(): LanguageService {
    TestBed.configureTestingModule({
      providers: [provideTranslateService({ fallbackLang: 'en', loader: provideTranslateLoader(FakeLoader) })],
    });
    return TestBed.inject(LanguageService);
  }

  const documentLang = () => TestBed.inject(DOCUMENT).documentElement.lang;
  const greeting = () => TestBed.inject(TranslateService).instant('greeting');

  beforeEach(() => {
    localStorage.clear();
    FakeLoader.failing = new Set();
  });
  afterEach(() => vi.restoreAllMocks());

  it('defaults to English when the browser language is not supported', async () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('de-DE');
    const service = setup();

    await firstValueFrom(service.init());

    expect(service.current()).toBe('en');
    expect(documentLang()).toBe('en');
  });

  it('uses the browser language when it is supported and nothing is saved', async () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('fr-CA');
    const service = setup();

    await firstValueFrom(service.init());

    expect(service.current()).toBe('fr');
    expect(greeting()).toBe('hello-fr');
  });

  it('prefers the saved language over the browser language', async () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-GB');
    localStorage.setItem(STORAGE_KEY, 'fr');
    const service = setup();

    await firstValueFrom(service.init());

    expect(service.current()).toBe('fr');
  });

  it('ignores an unsupported saved value', async () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US');
    localStorage.setItem(STORAGE_KEY, 'xx');
    const service = setup();

    await firstValueFrom(service.init());

    expect(service.current()).toBe('en');
  });

  it('switches language, saves the choice and updates the document language', async () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US');
    const service = setup();
    await firstValueFrom(service.init());

    expect(await firstValueFrom(service.use('fr'))).toBe(true);

    expect(service.current()).toBe('fr');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('fr');
    expect(documentLang()).toBe('fr');
    expect(greeting()).toBe('hello-fr');
  });

  it('keeps working when browser storage is unavailable', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    const service = setup();

    await firstValueFrom(service.init());
    await firstValueFrom(service.use('fr'));

    expect(service.current()).toBe('fr');
  });

  it('falls back to English at start-up when the preferred language cannot be loaded', async () => {
    localStorage.setItem(STORAGE_KEY, 'fr');
    FakeLoader.failing = new Set(['fr']);
    const service = setup();

    expect(await firstValueFrom(service.init())).toBe(true);

    expect(service.current()).toBe('en');
    expect(documentLang()).toBe('en');
    expect(greeting()).toBe('hello-en');
  });

  it('still starts, showing keys, when no language can be loaded', async () => {
    FakeLoader.failing = new Set(['en', 'fr']);
    const service = setup();

    expect(await firstValueFrom(service.init())).toBe(false);

    expect(service.current()).toBe('en');
    expect(greeting()).toBe('greeting');
  });

  it('keeps the current language when a switch fails, and reports it', async () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('en-US');
    const service = setup();
    await firstValueFrom(service.init());
    FakeLoader.failing = new Set(['fr']);

    expect(await firstValueFrom(service.use('fr'))).toBe(false);

    expect(service.current()).toBe('en');
    expect(documentLang()).toBe('en');
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    expect(service.switchFailure()).toBe('fr');

    FakeLoader.failing = new Set();
    await firstValueFrom(service.use('fr'));
    expect(service.switchFailure()).toBeNull();
  });
});
