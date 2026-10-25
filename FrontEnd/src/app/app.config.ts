import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { provideMissingTranslationHandler, provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { FallbackMissingTranslationHandler } from './core/i18n/fallback-missing-translation-handler';
import { DEFAULT_LANGUAGE, LanguageService } from './core/i18n/language.service';
import { TranslatedTitleStrategy } from './core/i18n/translated-title-strategy';

// English locale data is built in; French is registered for dates and numbers.
registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withFetch()),
    provideTranslateService({
      fallbackLang: DEFAULT_LANGUAGE,
      // Relative prefix resolves against <base href>, so the app also works under a sub-path (CR-18).
      // failOnError lets LanguageService detect a missing file and fall back instead of showing partial texts.
      loader: provideTranslateHttpLoader({ prefix: 'i18n/', suffix: '.json', failOnError: true }),
      missingTranslationHandler: provideMissingTranslationHandler(FallbackMissingTranslationHandler),
    }),
    // Load the user's language before the first render so no untranslated keys flash on screen.
    provideAppInitializer(() => firstValueFrom(inject(LanguageService).init())),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
  ],
};
