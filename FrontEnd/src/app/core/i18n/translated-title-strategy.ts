import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

/**
 * Route titles are translation keys; the document title follows the selected language so screen readers
 * announce each page in the user's language.
 */
@Injectable({ providedIn: 'root' })
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly translate = inject(TranslateService);
  private pageKey: string | undefined;

  constructor() {
    super();
    this.translate.onLangChange.subscribe(() => this.apply());
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.pageKey = this.buildTitle(snapshot);
    this.apply();
  }

  private apply(): void {
    const appName = this.translate.instant('app.name');
    this.title.setTitle(this.pageKey ? `${this.translate.instant(this.pageKey)} · ${appName}` : appName);
  }
}
