import { DestroyRef, Injectable, effect, inject, signal, untracked } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { hasPermission } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { INTERNAL_MESSAGING_API, InternalMessagingApi } from './internal-messaging.api';
import { LiveEvent, PendingMessage } from './internal-messaging.models';

const RETRY_DELAYS_MS = [2_000, 5_000, 10_000, 30_000, 60_000];

/**
 * The signed-in user's messaging state that outlives a page: the unread total for the navigation, the live event
 * stream, unsent drafts and messages still being sent.
 *
 * - The stream (Server-Sent Events, session cookie, nothing in the URL) carries ids only. Pages react to its events by
 *   fetching through the API, so access is re-checked on every fetch. On every (re)connection the stream sends
 *   `ready`, and pages resynchronise from the database: nothing depends on an event arriving.
 * - Drafts and pending messages live in memory only, and are cleared on sign-out or when another user signs in on
 *   this tab, so no message text is left behind in the browser.
 */
@Injectable({ providedIn: 'root' })
export class MessagingLiveService {
  private readonly api = inject(InternalMessagingApi);
  private readonly auth = inject(AuthService);

  private readonly unreadSignal = signal(0);
  readonly unread = this.unreadSignal.asReadonly();

  private readonly connectedSignal = signal(false);
  /** True while the live stream is open. Pages show nothing different; tests and diagnostics read it. */
  readonly connected = this.connectedSignal.asReadonly();

  private readonly pendingSignal = signal<PendingMessage[]>([]);
  readonly pending = this.pendingSignal.asReadonly();

  private readonly eventSubject = new Subject<LiveEvent>();
  readonly events: Observable<LiveEvent> = this.eventSubject.asObservable();

  private readonly drafts = new Map<string, string>();
  private source: EventSource | null = null;
  private userId: number | null = null;
  private retry = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private unreadTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const me = this.auth.me();
      const employee = me && !hasPermission(me, 'MESSAGING_SUPPLIER') ? me.id : null;
      untracked(() => this.switchUser(employee));
    });
    inject(DestroyRef).onDestroy(() => this.switchUser(null));
  }

  /** Re-reads the unread total from the database (debounced, so a burst of events costs one request). */
  refreshUnread(): void {
    if (this.userId === null) {
      return;
    }
    if (this.unreadTimer) {
      clearTimeout(this.unreadTimer);
    }
    this.unreadTimer = setTimeout(() => {
      this.unreadTimer = null;
      this.api.unread().subscribe({
        next: (u) => this.unreadSignal.set(u.total),
        error: () => undefined,
      });
    }, 250);
  }

  draft(key: string): string {
    return this.drafts.get(key) ?? '';
  }

  setDraft(key: string, text: string): void {
    if (text) {
      this.drafts.set(key, text);
    } else {
      this.drafts.delete(key);
    }
  }

  pendingFor(conversationId: number): PendingMessage[] {
    return this.pendingSignal().filter((p) => p.conversationId === conversationId);
  }

  addPending(message: PendingMessage): void {
    this.pendingSignal.update((list) => [...list.filter((p) => p.clientMessageId !== message.clientMessageId), message]);
  }

  removePending(clientMessageId: string): void {
    this.pendingSignal.update((list) => list.filter((p) => p.clientMessageId !== clientMessageId));
  }

  private switchUser(userId: number | null): void {
    if (userId === this.userId) {
      return;
    }
    this.disconnect();
    // Another person, or nobody: nothing of the previous user's messaging stays in this tab.
    this.drafts.clear();
    this.pendingSignal.set([]);
    this.unreadSignal.set(0);
    this.userId = userId;
    if (userId !== null) {
      this.connect();
      this.refreshUnread();
    }
  }

  private connect(): void {
    if (typeof EventSource === 'undefined' || this.userId === null) {
      return;
    }
    const source = new EventSource(`${INTERNAL_MESSAGING_API}/stream`, { withCredentials: true });
    this.source = source;
    source.addEventListener('ready', () => {
      this.retry = 0;
      this.connectedSignal.set(true);
      this.eventSubject.next({ type: 'ready' });
      this.refreshUnread();
    });
    source.addEventListener('message', (event) => {
      const data = parse(event);
      if (data && typeof data['conversationId'] === 'number' && typeof data['messageId'] === 'number') {
        this.eventSubject.next({ type: 'message', conversationId: data['conversationId'], messageId: data['messageId'] });
        this.refreshUnread();
      }
    });
    source.addEventListener('read', (event) => {
      const data = parse(event);
      if (data && typeof data['conversationId'] === 'number') {
        this.eventSubject.next({ type: 'read', conversationId: data['conversationId'] });
        this.refreshUnread();
      }
    });
    source.onerror = () => {
      this.connectedSignal.set(false);
      // The browser retries an interrupted stream on its own; a refused one (401, 403, 503) is closed for good.
      if (source.readyState === EventSource.CLOSED && this.source === source) {
        this.source = null;
        this.scheduleReconnect();
      }
    };
  }

  private scheduleReconnect(): void {
    if (this.userId === null || this.retryTimer) {
      return;
    }
    const delay = RETRY_DELAYS_MS[Math.min(this.retry, RETRY_DELAYS_MS.length - 1)];
    this.retry++;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      if (this.userId !== null && !this.source) {
        this.connect();
        this.refreshUnread();
      }
    }, delay);
  }

  private disconnect(): void {
    this.source?.close();
    this.source = null;
    this.connectedSignal.set(false);
    this.retry = 0;
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    if (this.unreadTimer) {
      clearTimeout(this.unreadTimer);
      this.unreadTimer = null;
    }
  }
}

function parse(event: Event): Record<string, unknown> | null {
  try {
    const data = (event as MessageEvent<string>).data;
    const value: unknown = JSON.parse(data);
    return value && typeof value === 'object' ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}
