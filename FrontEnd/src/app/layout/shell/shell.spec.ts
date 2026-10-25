import { BreakpointObserver } from '@angular/cdk/layout';
import { DOCUMENT } from '@angular/common';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LanguageService } from '../../core/i18n/language.service';
import { NAVIGATION } from '../navigation';
import { Shell } from './shell';

@Component({ template: '<h1 tabindex="-1">Status page</h1>' })
class StatusStubPage {}

describe('Shell', () => {
  let fixture: ComponentFixture<Shell>;
  const element = () => fixture.nativeElement as HTMLElement;

  async function create(drawerMode = false): Promise<void> {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [Shell],
      providers: [
        provideRouter([{ path: 'system-status', component: StatusStubPage }]),
        provideTranslateService({ fallbackLang: 'en' }),
        ...(drawerMode
          ? [{ provide: BreakpointObserver, useValue: { observe: () => of({ matches: true, breakpoints: {} }) } }]
          : []),
      ],
    });
    fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
  }

  describe('on desktop', () => {
    beforeEach(() => create());

    it('offers a skip link to the main content', () => {
      const skip = element().querySelector<HTMLAnchorElement>('a.skip-link');
      expect(skip?.getAttribute('href')).toBe('#main');
      expect(element().querySelector('main#main')).not.toBeNull();
    });

    it('moves focus to the main content without changing the route', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/system-status');
      await fixture.whenStable();
      const navigate = vi.spyOn(router, 'navigateByUrl');

      const skip = element().querySelector<HTMLAnchorElement>('a.skip-link')!;
      const click = new MouseEvent('click', { bubbles: true, cancelable: true });
      skip.dispatchEvent(click);

      expect(click.defaultPrevented).toBe(true);
      expect(TestBed.inject(DOCUMENT).activeElement?.id).toBe('main');
      expect(router.url).toBe('/system-status');
      expect(navigate).not.toHaveBeenCalled();
    });

    it('renders the language selector with one button per language', () => {
      const buttons = element().querySelectorAll<HTMLButtonElement>('app-language-select button');
      expect(Array.from(buttons).map((b) => b.getAttribute('lang'))).toEqual(['en', 'fr']);
    });

    it('switches language from the selector and marks the active choice', async () => {
      const french = element().querySelector<HTMLButtonElement>('app-language-select button[lang="fr"]');
      french?.click();
      await fixture.whenStable();

      expect(TestBed.inject(LanguageService).current()).toBe('fr');
      expect(TestBed.inject(DOCUMENT).documentElement.lang).toBe('fr');
      expect(french?.getAttribute('aria-pressed')).toBe('true');
    });

    it('lists every navigation item and flags planned modules with their sprint', () => {
      const links = element().querySelectorAll('nav .nav-link');
      const itemCount = NAVIGATION.reduce((count, group) => count + group.items.length, 0);
      const plannedCount = NAVIGATION.flatMap((group) => group.items).filter((item) => item.plannedSprint).length;

      expect(links.length).toBe(itemCount);
      expect(element().querySelectorAll('nav .nav-planned').length).toBe(plannedCount);
    });

    it('collapses the sidebar to a rail and remembers the choice', async () => {
      const toggle = element().querySelector<HTMLButtonElement>('.collapse-toggle');
      expect(toggle?.getAttribute('aria-expanded')).toBe('true');

      toggle?.click();
      await fixture.whenStable();

      expect(element().querySelector('.shell')?.classList).toContain('is-collapsed');
      expect(toggle?.getAttribute('aria-expanded')).toBe('false');
      expect(localStorage.getItem('epi.sidebar.collapsed')).toBe('true');
    });

    it('does not present the sidebar as a dialog', () => {
      const sidebar = element().querySelector('#app-sidebar');
      expect(sidebar?.getAttribute('role')).toBeNull();
      expect(sidebar?.getAttribute('aria-modal')).toBeNull();
    });
  });

  describe('on tablet and phone', () => {
    beforeEach(() => create(true));

    it('presents the open drawer as a modal dialog', async () => {
      const sidebar = element().querySelector('#app-sidebar');
      expect(sidebar?.getAttribute('role')).toBe('dialog');
      expect(sidebar?.getAttribute('aria-modal')).toBeNull();
      expect(sidebar?.hasAttribute('inert')).toBe(true);

      element().querySelector<HTMLButtonElement>('.menu-button')?.click();
      await fixture.whenStable();

      expect(sidebar?.getAttribute('aria-modal')).toBe('true');
      expect(sidebar?.hasAttribute('inert')).toBe(false);
    });
  });
});
