import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'success' | 'error' | 'info';

export interface AppNotification {
  id: number;
  kind: NotificationKind;
  /** Translation key and parameters; the toaster translates at render time so a language switch applies. */
  key: string;
  params?: Record<string, unknown>;
}

const DISMISS_AFTER_MS = 6000;

/** Short, non-blocking confirmations and failures ("Draft saved", "Language could not be saved"). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  private readonly items = signal<AppNotification[]>([]);
  readonly notifications = this.items.asReadonly();

  success(key: string, params?: Record<string, unknown>): void {
    this.show('success', key, params);
  }

  error(key: string, params?: Record<string, unknown>): void {
    this.show('error', key, params);
  }

  info(key: string, params?: Record<string, unknown>): void {
    this.show('info', key, params);
  }

  dismiss(id: number): void {
    this.items.update((items) => items.filter((item) => item.id !== id));
  }

  private show(kind: NotificationKind, key: string, params?: Record<string, unknown>): void {
    const id = this.nextId++;
    // Keep the three most recent so a burst of messages never covers the page.
    this.items.update((items) => [...items.slice(-2), { id, kind, key, params }]);
    setTimeout(() => this.dismiss(id), DISMISS_AFTER_MS);
  }
}
