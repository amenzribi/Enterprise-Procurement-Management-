import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { Me } from '../core/auth/auth.models';
import { AuthService } from '../core/auth/auth.service';
import { UserMenu } from './user-menu';

describe('UserMenu', () => {
  let fixture: ComponentFixture<UserMenu>;
  const me = signal<Me | null>({
    id: 1,
    email: 'manager.tn@demo.local',
    displayName: 'Karim Haddad',
    company: { id: 3, name: 'Atlas Mechanical Sfax (Demo)' },
    roles: ['DEPARTMENT_MANAGER'],
  });
  const auth = { me, signOut: vi.fn(() => of(undefined)) };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [UserMenu],
      providers: [provideTranslateService({ fallbackLang: 'en' }), { provide: AuthService, useValue: auth }],
    });
    fixture = TestBed.createComponent(UserMenu);
    await fixture.whenStable();
  });

  it('shows who is signed in and for which company', () => {
    const trigger = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    expect(trigger.textContent).toContain('Karim Haddad');
    expect(trigger.textContent).toContain('Atlas Mechanical Sfax (Demo)');
    expect(trigger.querySelector('.avatar')?.textContent?.trim()).toBe('KH');
  });

  it('offers sign-out with roles once opened', async () => {
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();

    const menu = document.querySelector('[role="menu"]');
    expect(menu?.textContent).toContain('auth.roles.DEPARTMENT_MANAGER');
    menu?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.click();
    expect(auth.signOut).toHaveBeenCalled();
  });

  it('renders nothing when nobody is signed in', async () => {
    me.set(null);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('button')).toBeNull();
  });
});
