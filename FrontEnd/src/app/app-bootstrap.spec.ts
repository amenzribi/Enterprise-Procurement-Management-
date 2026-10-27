import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideMissingTranslationHandler, provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { AuthService } from './core/auth/auth.service';
import { sessionInterceptor } from './core/auth/session.interceptor';
import { FallbackMissingTranslationHandler } from './core/i18n/fallback-missing-translation-handler';

/**
 * QA1-D-03: the translation loader's first request runs while TranslateService is being created. If the HTTP
 * interceptor pulled in a service that depends on TranslateService (AuthService -> LanguageService), Angular threw
 * NG0200 (circular dependency) and the English fallback never loaded.
 */
describe('application bootstrap', () => {
  it('loads the fallback translations through the session interceptor without a circular dependency', () => {
    const logged: string[] = [];
    const capture = (...args: unknown[]) => logged.push(args.map(String).join(' '));
    const error = vi.spyOn(console, 'error').mockImplementation(capture);
    const warn = vi.spyOn(console, 'warn').mockImplementation(capture);
    try {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(withInterceptors([sessionInterceptor])),
          provideHttpClientTesting(),
          provideTranslateService({
            fallbackLang: 'en',
            loader: provideTranslateHttpLoader({ prefix: 'i18n/', suffix: '.json', failOnError: true }),
            missingTranslationHandler: provideMissingTranslationHandler(FallbackMissingTranslationHandler),
          }),
        ],
      });
      // The real services the app creates at start-up, in the order the app initializer uses them.
      TestBed.inject(TranslateService);
      TestBed.inject(AuthService);

      const http = TestBed.inject(HttpTestingController);
      http.expectOne('i18n/en.json').flush({ app: { name: 'Procurement Intelligence' } });

      expect(logged.filter((line) => line.includes('NG0200') || line.includes('failed to load'))).toEqual([]);
      expect(TestBed.inject(TranslateService).instant('app.name')).toBe('Procurement Intelligence');
    } finally {
      error.mockRestore();
      warn.mockRestore();
    }
  });
});
