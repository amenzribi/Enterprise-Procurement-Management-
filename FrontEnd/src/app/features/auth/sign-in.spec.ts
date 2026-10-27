import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { SignInPage } from './sign-in';

describe('SignInPage', () => {
  let fixture: ComponentFixture<SignInPage>;
  const auth = { signIn: vi.fn() };
  const element = () => fixture.nativeElement as HTMLElement;

  async function create(query: Record<string, string> = {}): Promise<void> {
    auth.signIn.mockReset();
    TestBed.configureTestingModule({
      imports: [SignInPage],
      providers: [
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: AuthService, useValue: auth },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    fixture = TestBed.createComponent(SignInPage);
    await fixture.whenStable();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  }

  async function fill(email: string, password: string): Promise<void> {
    const emailInput = element().querySelector<HTMLInputElement>('#sign-in-email')!;
    const passwordInput = element().querySelector<HTMLInputElement>('#sign-in-password')!;
    emailInput.value = email;
    emailInput.dispatchEvent(new Event('input'));
    passwordInput.value = password;
    passwordInput.dispatchEvent(new Event('input'));
    element().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('labels its fields for password managers and assistive technology', async () => {
    await create();
    expect(element().querySelector('#sign-in-email')?.getAttribute('autocomplete')).toBe('username');
    expect(element().querySelector('#sign-in-password')?.getAttribute('autocomplete')).toBe('current-password');
    expect(element().querySelector('label[for="sign-in-email"]')).not.toBeNull();
    expect(element().querySelector('app-language-select')).not.toBeNull();
  });

  it('validates locally before calling the server', async () => {
    await create();
    await fill('', '');

    expect(auth.signIn).not.toHaveBeenCalled();
    expect(element().querySelector('#sign-in-email-error')).not.toBeNull();
    expect(element().querySelector('#sign-in-password-error')).not.toBeNull();
  });

  it('shows the same message whatever the reason for refusal (no account enumeration)', async () => {
    await create();
    for (const status of [401, 423, 429]) {
      auth.signIn.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status })));
      await fill('ceo@demo.local', 'wrong');
      expect(element().querySelector('[role="alert"]')?.textContent).toContain('auth.signIn.errors.invalid-credentials');
    }
  });

  it('returns to the page the user wanted, but only inside the application', async () => {
    await create({ returnUrl: '/requests/12' });
    auth.signIn.mockReturnValue(of({}));
    await fill('requester.tn@demo.local', 'secret');
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/requests/12');

    TestBed.resetTestingModule();
    await create({ returnUrl: 'https://evil.example' });
    auth.signIn.mockReturnValue(of({}));
    await fill('requester.tn@demo.local', 'secret');
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/requests');
  });

  it('explains that the session expired', async () => {
    await create({ reason: 'expired' });
    expect(element().querySelector('.notice[role="status"]')?.textContent).toContain('auth.signIn.expired');
  });
});
