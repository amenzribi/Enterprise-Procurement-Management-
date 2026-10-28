import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import localeArTn from '@angular/common/locales/ar-TN';
import localeEnGb from '@angular/common/locales/en-GB';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { provideMissingTranslationHandler, provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { firstValueFrom, switchMap } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { sessionInterceptor } from './core/auth/session.interceptor';
import { FallbackMissingTranslationHandler } from './core/i18n/fallback-missing-translation-handler';
import { DEFAULT_LANGUAGE, LanguageService } from './core/i18n/language.service';
import { TranslatedTitleStrategy } from './core/i18n/translated-title-strategy';

// The English interface formats dates and numbers as en-GB (day/month, as used in Tunisia and France).
registerLocaleData(localeEnGb);
registerLocaleData(localeFr);
// Arabic uses ar-TN: Arabic texts with Latin digits for amounts, dates and numbers (S2-AC-01).
registerLocaleData(localeArTn);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      // Spring Security's SPA CSRF setup: token in the XSRF-TOKEN cookie, echoed in the X-XSRF-TOKEN header.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([sessionInterceptor]),
    ),
    provideTranslateService({
      fallbackLang: DEFAULT_LANGUAGE,
      // Relative prefix resolves against <base href>, so the app also works under a sub-path (CR-18).
      // failOnError lets LanguageService detect a missing file and fall back instead of showing partial texts.
      loader: provideTranslateHttpLoader({ prefix: 'i18n/', suffix: '.json', failOnError: true }),
      missingTranslationHandler: provideMissingTranslationHandler(FallbackMissingTranslationHandler),
    }),
    // Load the language and restore an existing session before the first render, so guards know who is signed in
    // and no untranslated keys flash on screen.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      return firstValueFrom(inject(LanguageService).init().pipe(switchMap(() => auth.init())));
    }),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
  ],
};
