import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { EmailChangeConfirmPage } from './email-change-confirm';

describe('EmailChangeConfirmPage', () => {
  let http: HttpTestingController;

  function open(hash: string) {
    window.history.replaceState(null, '', '/e' + hash);
    TestBed.configureTestingModule({
      imports: [EmailChangeConfirmPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(EmailChangeConfirmPage);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => {
    http.verify();
    window.history.replaceState(null, '', '/');
  });

  it('posts the fragment token only when the owner confirms', async () => {
    const fixture = open('#raw-token');
    // Opening the link (e.g. by a mail scanner) confirms nothing.
    http.expectNone('/api/v1/auth/email-change/confirm');
    const submit = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button')).find(
      (button) => button.textContent!.includes('emailChange.submit'),
    )!;
    submit.click();
    const request = http.expectOne('/api/v1/auth/email-change/confirm');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ token: 'raw-token' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(fixture.componentInstance.state()).toBe('done');
  });

  it('shows the neutral invalid-link message for a 404', () => {
    const fixture = open('#used');
    fixture.componentInstance.confirm();
    http.expectOne('/api/v1/auth/email-change/confirm').flush({ code: 'not-found' }, { status: 404, statusText: 'Not Found' });
    expect(fixture.componentInstance.state()).toBe('invalid');
  });

  it('offers a retry when the server is busy', () => {
    const second = open('#busy');
    second.componentInstance.confirm();
    http.expectOne('/api/v1/auth/email-change/confirm').flush({ code: 'busy' }, { status: 503, statusText: 'Busy' });
    expect(second.componentInstance.state()).toBe('error');
  });

  it('treats a link without a token as invalid without calling the server', () => {
    const fixture = open('');
    expect(fixture.componentInstance.state()).toBe('invalid');
    http.expectNone('/api/v1/auth/email-change/confirm');
  });
});
