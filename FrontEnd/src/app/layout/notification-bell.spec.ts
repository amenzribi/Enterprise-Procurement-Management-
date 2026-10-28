import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import en from '../../../public/i18n/en.json';
import { InAppNotifications } from '../features/notifications/in-app-notifications';
import { NotificationBell } from './notification-bell';

describe('NotificationBell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let http: HttpTestingController;
  const element = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [NotificationBell],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService({ fallbackLang: 'en' })],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(NotificationBell);
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('shows the unread count from the server', async () => {
    TestBed.inject(InAppNotifications).refreshCount();
    http.expectOne('/api/v1/notifications/unread-count').flush({ unread: 3 });
    await fixture.whenStable();
    expect(element().querySelector('.count')?.textContent?.trim()).toBe('3');
  });

  it('names the bell with the same count it shows, and gives the exact count as a tooltip (QA2-D-01)', async () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { notifications: { open: en.notifications.open, exactCount: en.notifications.exactCount } });
    translate.use('en');
    TestBed.inject(InAppNotifications).refreshCount();
    http.expectOne('/api/v1/notifications/unread-count').flush({ unread: 192 });
    await fixture.whenStable();
    const bell = element().querySelector('button.bell')!;
    expect(bell.querySelector('.count')?.textContent?.trim()).toBe('99+');
    expect(bell.getAttribute('aria-label')).toContain('99+');
    expect(bell.getAttribute('title')).toContain('192');
  });

  it('lists the latest notifications when opened; choosing one marks it read and opens the request', async () => {
    const router = TestBed.inject(Router);
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    element().querySelector<HTMLButtonElement>('button.bell')!.click();
    await fixture.whenStable();
    http.expectOne((request) => request.url === '/api/v1/notifications').flush({
      items: [
        { id: 5, kind: 'TASK_ASSIGNED', requestId: 7, title: 'N-418 is waiting for your approval', body: 'Sami · 8 400 TND', link: '/requests/7', createdAt: '2026-11-02T08:00:00Z' },
        { id: 6, kind: 'APPROVED', requestId: 8, title: 'Unsafe link', link: 'https://evil.example/x', createdAt: '2026-11-02T07:00:00Z', readAt: '2026-11-02T07:30:00Z' },
      ],
      page: 0,
      size: 10,
      totalItems: 2,
      unreadCount: 1,
    });
    await fixture.whenStable();

    const items = document.querySelectorAll<HTMLButtonElement>('.item');
    expect(items[0].textContent).toContain('N-418 is waiting for your approval');
    expect(items[0].classList).toContain('is-unread');
    items[0].click();
    http.expectOne('/api/v1/notifications/5/read').flush(null);
    expect(navigateByUrl).toHaveBeenCalledWith('/requests/7');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('never follows a link outside the application', async () => {
    const router = TestBed.inject(Router);
    const navigateByUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    element().querySelector<HTMLButtonElement>('button.bell')!.click();
    await fixture.whenStable();
    http.expectOne((request) => request.url === '/api/v1/notifications').flush({
      items: [{ id: 6, kind: 'APPROVED', requestId: 8, title: 'x', link: '//evil.example/x', createdAt: '2026-11-02T07:00:00Z', readAt: '2026-11-02T07:30:00Z' }],
      page: 0,
      size: 10,
      totalItems: 1,
    });
    await fixture.whenStable();
    document.querySelector<HTMLButtonElement>('.item')!.click();
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/requests', 8]);
  });
});
