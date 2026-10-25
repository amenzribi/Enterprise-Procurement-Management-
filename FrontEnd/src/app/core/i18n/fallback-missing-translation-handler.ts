import { Injectable, isDevMode } from '@angular/core';
import { MissingTranslationHandler, MissingTranslationHandlerParams } from '@ngx-translate/core';

/**
 * When a key has no translation, shows the `fallback` interpolation parameter if one is given (for example a
 * technical name reported by the server), otherwise the key itself. Never returns an empty text.
 * In development a warning names the missing key, so gaps are noticed before release.
 * Usage: `{{ 'health.components.' + name | translate: { fallback: name } }}`.
 */
@Injectable()
export class FallbackMissingTranslationHandler implements MissingTranslationHandler {
  handle(params: MissingTranslationHandlerParams): string {
    if (isDevMode()) {
      console.warn(`[i18n] Missing translation for "${params.key}" (${params.translateService.getCurrentLang()})`);
    }
    const fallback = (params.interpolateParams as Record<string, unknown> | undefined)?.['fallback'];
    return typeof fallback === 'string' && fallback ? fallback : params.key;
  }
}
