import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  provideMissingTranslationHandler,
  provideTranslateService,
  TranslatePipe,
  TranslateService,
} from '@ngx-translate/core';
import en from '../../../../public/i18n/en.json';
import fr from '../../../../public/i18n/fr.json';
import { FallbackMissingTranslationHandler } from './fallback-missing-translation-handler';

const MISSING_KEY = 'qa.doesNotExist';

@Component({
  imports: [TranslatePipe],
  template: `<p id="missing">{{ key | translate }}</p>
    <p id="with-fallback">{{ key | translate: { fallback: 'mailServer' } }}</p>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class MissingKeyHost {
  readonly key = MISSING_KEY;
}

// QA-10.9: a key absent from both translation files never renders blank and is reported in development.
describe('FallbackMissingTranslationHandler with the real translation files', () => {
  let translate: TranslateService;
  let warn: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MissingKeyHost],
      providers: [
        provideTranslateService({
          fallbackLang: 'en',
          missingTranslationHandler: provideMissingTranslationHandler(FallbackMissingTranslationHandler),
        }),
      ],
    });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.setTranslation('fr', fr);
    warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => warn.mockRestore());

  for (const lang of ['en', 'fr']) {
    it(`returns the key, never an empty text, and warns in dev mode (${lang})`, async () => {
      translate.use(lang);

      const text = translate.instant(MISSING_KEY);

      expect(text).toBe(MISSING_KEY);
      expect(warn).toHaveBeenCalledWith(expect.stringContaining(MISSING_KEY));
    });

    it(`renders the key or the given fallback through the translate pipe (${lang})`, async () => {
      translate.use(lang);
      const fixture = TestBed.createComponent(MissingKeyHost);
      await fixture.whenStable();
      const element = fixture.nativeElement as HTMLElement;

      expect(element.querySelector('#missing')?.textContent?.trim()).toBe(MISSING_KEY);
      expect(element.querySelector('#with-fallback')?.textContent?.trim()).toBe('mailServer');
      expect(warn).toHaveBeenCalledWith(expect.stringContaining(MISSING_KEY));
    });
  }

  it('still translates existing keys without warning', () => {
    translate.use('fr');
    const key = 'app.suite';

    expect(translate.instant(key)).toBe(fr.app.suite);
    expect(warn).not.toHaveBeenCalled();
  });
});
