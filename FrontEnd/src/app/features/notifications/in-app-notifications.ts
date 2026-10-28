import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { NotificationPage } from '../approvals/approval.models';

const API = '/api/v1/notifications';

/**
 * In-app notifications (S2-AC-10): unread count for the bell, the latest items, mark one or all as read. Refreshed on
 * navigation and when the panel opens, never by background polling.
 */
@Injectable({ providedIn: 'root' })
export class InAppNotifications {
  private readonly http = inject(HttpClient);

  private readonly unreadValue = signal(0);
  readonly unread = this.unreadValue.asReadonly();

  refreshCount(): void {
    this.http.get<{ unread: number }>(`${API}/unread-count`).subscribe({
      next: (body) => this.unreadValue.set(body.unread ?? 0),
      error: () => undefined,
    });
  }

  latest(size = 10): Observable<NotificationPage> {
    const params = new HttpParams().set('page', 0).set('size', size);
    return this.http
      .get<NotificationPage>(API, { params })
      .pipe(tap((page) => page.unreadCount !== undefined && this.unreadValue.set(page.unreadCount)));
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`${API}/${id}/read`, {}).pipe(tap(() => this.unreadValue.update((count) => Math.max(0, count - 1))));
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${API}/read-all`, {}).pipe(tap(() => this.unreadValue.set(0)));
  }
}
