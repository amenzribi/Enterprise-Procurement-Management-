import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { Skeleton } from '../../shared/ui/skeleton';
import { RequestsApi } from '../requests/requests.api';
import { RequestListItem } from '../requests/request.models';
import { InternalMessagingApi } from './internal-messaging.api';
import { ConversationDetail, Message, PendingMessage } from './internal-messaging.models';
import { MAX_BODY, mergeMessages, newClientMessageId, newestId, readMarkerFor } from './message-list';
import { MessagingLiveService } from './messaging-live.service';
import { RequestCardView } from './request-card';

type LoadState = 'loading' | 'ready' | 'error' | 'not-found';

/**
 * One conversation: its history (paged), live arrivals, the read marker and the composer.
 *
 * - New messages come from the database: a live event only tells the thread to ask for everything after the newest
 *   id it holds, so a missed or repeated event changes nothing (messages are merged by id).
 * - The read marker moves only for messages actually on screen: when the end of the list is visible in a visible tab.
 * - Sending shows the message at once as "Sending"; a failure keeps it with "Retry", which reuses the same client id,
 *   so the server stores it once whatever happened to the first attempt.
 */
@Component({
  selector: 'app-message-thread',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, ErrorState, Icon, RequestCardView, Skeleton, TranslatePipe],
  template: `
    @switch (state()) {
      @case ('loading') {
        <p class="visually-hidden" role="status">{{ 'internalMessaging.thread.loading' | translate }}</p>
        <div class="loading">
          <app-skeleton height="3rem" width="60%" />
          <app-skeleton height="3rem" width="45%" />
          <app-skeleton height="3rem" width="55%" />
        </div>
      }
      @case ('not-found') {
        <app-error-state [heading]="'internalMessaging.thread.notFound' | translate" [message]="'internalMessaging.thread.notFoundHint' | translate" />
      }
      @case ('error') {
        <app-error-state
          [heading]="'internalMessaging.thread.loadError' | translate"
          [retryLabel]="'common.retry' | translate"
          (retry)="reload()"
        />
      }
      @default {
        <div class="log" #log role="log" [attr.aria-label]="'internalMessaging.thread.history' | translate" tabindex="0">
          @if (hasOlder()) {
            <div class="older">
              <button appButton variant="ghost" size="small" type="button" [busy]="loadingOlder()" [disabled]="loadingOlder()" (click)="loadOlder()">
                {{ 'internalMessaging.thread.older' | translate }}
              </button>
            </div>
          }
          @for (m of messages(); track m.id; let i = $index) {
            @if (i === 0 || dayOf(messages()[i - 1]) !== dayOf(m)) {
              <p class="day"><time [attr.datetime]="m.createdAt">{{ m.createdAt | date: 'fullDate' : undefined : language.locale() }}</time></p>
            }
            @if (m.kind === 'SYSTEM') {
              <p class="system">{{ systemText(m) | translate: systemParams(m) }}</p>
            } @else {
              <article class="message" [class.mine]="m.mine">
                @if (!m.mine && showSender()) {
                  <p class="sender" dir="auto">{{ m.sender.displayName }}</p>
                }
                <div class="bubble">
                  <p class="body" dir="auto">{{ m.bodyText }}</p>
                  @if (m.request; as card) {
                    <app-request-card [card]="card" />
                  }
                </div>
                <p class="meta">
                  <time [attr.datetime]="m.createdAt">{{ m.createdAt | date: 'shortTime' : undefined : language.locale() }}</time>
                  @if (m.mine) {
                    <span class="state"><app-icon name="check" [size]="12" /> {{ 'internalMessaging.thread.sent' | translate }}</span>
                  }
                </p>
              </article>
            }
          } @empty {
            @if (pending().length === 0) {
              <div class="empty">
                <app-icon name="messages" [size]="28" />
                <p>{{ 'internalMessaging.thread.empty' | translate }}</p>
              </div>
            }
          }
          @for (p of pending(); track p.clientMessageId) {
            <article class="message mine" [class.is-failed]="p.state === 'failed'">
              <div class="bubble"><p class="body" dir="auto">{{ p.bodyText }}</p></div>
              <p class="meta" [attr.role]="p.state === 'failed' ? 'alert' : null">
                @if (p.state === 'sending') {
                  <span class="state">{{ 'internalMessaging.thread.sending' | translate }}</span>
                } @else {
                  <span class="state failed"><app-icon name="triangle-alert" [size]="12" /> {{ ('errors.' + (p.errorCode || 'network-error')) | translate }}</span>
                  <button type="button" class="link" (click)="retry(p)">{{ 'internalMessaging.thread.retry' | translate }}</button>
                  <button type="button" class="link" (click)="discard(p)">{{ 'internalMessaging.thread.discard' | translate }}</button>
                }
              </p>
            </article>
          }
          <span class="end" #end aria-hidden="true"></span>
        </div>

        @if (notice(); as n) {
          <p class="notice" role="status">
            <app-icon name="info" [size]="16" />
            <span>{{ n | translate }}</span>
            <button type="button" class="link" (click)="notice.set(null)">{{ 'internalMessaging.thread.dismiss' | translate }}</button>
          </p>
        }

        <form class="composer" (submit)="$event.preventDefault(); send()" novalidate>
          @if (attached(); as request) {
            <div class="attached">
              <app-icon name="paperclip" [size]="14" />
              <bdi class="tabular">{{ request.number }}</bdi>
              <span class="attached-title">{{ request.title }}</span>
              <button type="button" class="icon-only" (click)="attached.set(null)" [attr.aria-label]="'internalMessaging.composer.removeRequest' | translate">
                <app-icon name="x" [size]="14" />
              </button>
            </div>
          }
          @if (picking()) {
            <div class="picker">
              <label class="visually-hidden" [for]="'attach-' + uid">{{ 'internalMessaging.composer.findRequest' | translate }}</label>
              <input
                [id]="'attach-' + uid"
                type="search"
                [placeholder]="'internalMessaging.composer.findRequest' | translate"
                [value]="pickQuery()"
                (input)="searchRequests($any($event.target).value)"
                (keydown.escape)="picking.set(false)"
              />
              <ul class="results">
                @for (r of pickResults(); track r.id) {
                  <li>
                    <button type="button" class="result" (click)="attach(r)">
                      <bdi class="tabular">{{ r.number || ('requests.list.draftNumber' | translate) }}</bdi>
                      <span>{{ r.title }}</span>
                    </button>
                  </li>
                } @empty {
                  <li class="muted">{{ 'internalMessaging.composer.noRequests' | translate }}</li>
                }
              </ul>
            </div>
          }
          <label class="visually-hidden" [for]="'compose-' + uid">{{ 'internalMessaging.composer.label' | translate }}</label>
          <textarea
            #composer
            [id]="'compose-' + uid"
            rows="2"
            [attr.maxlength]="max"
            [placeholder]="'internalMessaging.composer.placeholder' | translate"
            [value]="draft()"
            (input)="setDraft($any($event.target).value)"
            (keydown.control.enter)="send()"
            (keydown.meta.enter)="send()"
            [attr.aria-describedby]="'compose-hint-' + uid"
          ></textarea>
          <div class="composer-bar">
            @if (allowAttach()) {
              <button type="button" class="icon-only" (click)="picking.set(!picking())" [attr.aria-expanded]="picking()" [attr.aria-label]="'internalMessaging.composer.attachRequest' | translate" [attr.title]="'internalMessaging.composer.attachRequest' | translate">
                <app-icon name="paperclip" [size]="18" />
              </button>
            }
            <span class="hint" [id]="'compose-hint-' + uid">
              @if (draft().length > max - 400) {
                {{ 'internalMessaging.composer.remaining' | translate: { count: max - draft().length } }}
              } @else {
                {{ 'internalMessaging.composer.shortcut' | translate }}
              }
            </span>
            <button appButton type="submit" size="small" [disabled]="!draft().trim()">
              <app-icon name="send" [size]="16" />
              {{ 'internalMessaging.composer.send' | translate }}
            </button>
          </div>
        </form>
      }
    }
  `,
  styleUrl: './message-thread.scss',
})
export class MessageThread {
  readonly conversationId = input.required<number>();
  /** Shown above other people's messages; off in a one-to-one conversation, where the sender is obvious. */
  readonly showSender = input(true);
  readonly allowAttach = input(true);
  /** Emits the detail once loaded (the page uses it for its header). */
  readonly loaded = output<ConversationDetail>();

  private readonly api = inject(InternalMessagingApi);
  private readonly requestsApi = inject(RequestsApi);
  protected readonly live = inject(MessagingLiveService);
  protected readonly language = inject(LanguageService);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  private static nextId = 0;
  protected readonly uid = ++MessageThread.nextId;
  protected readonly max = MAX_BODY;

  protected readonly state = signal<LoadState>('loading');
  protected readonly messages = signal<Message[]>([]);
  protected readonly hasOlder = signal(false);
  protected readonly loadingOlder = signal(false);
  protected readonly notice = signal<string | null>(null);
  protected readonly draft = signal('');
  protected readonly attached = signal<RequestListItem | null>(null);
  protected readonly picking = signal(false);
  protected readonly pickQuery = signal('');
  protected readonly pickResults = signal<RequestListItem[]>([]);
  protected readonly pending = computed(() =>
    this.live.pending().filter((p) => p.conversationId === this.conversationId()),
  );

  private readonly log = viewChild<ElementRef<HTMLElement>>('log');
  private readonly end = viewChild<ElementRef<HTMLElement>>('end');
  private lastRead: number | null = null;
  private endVisible = false;
  private observer: IntersectionObserver | null = null;
  private pickTimer: ReturnType<typeof setTimeout> | null = null;
  private fetchingNewer = false;

  constructor() {
    const destroyRef = inject(DestroyRef);
    effect(() => {
      const id = this.conversationId();
      untracked(() => this.open(id));
    });
    this.live.events.pipe(takeUntilDestroyed(destroyRef)).subscribe((event) => {
      if (this.state() !== 'ready') {
        return;
      }
      if (event.type === 'ready' || (event.type === 'message' && event.conversationId === this.conversationId())) {
        this.fetchNewer();
      }
    });
    const onVisibility = () => this.markReadIfSeen();
    document.addEventListener('visibilitychange', onVisibility);
    destroyRef.onDestroy(() => {
      document.removeEventListener('visibilitychange', onVisibility);
      this.observer?.disconnect();
      if (this.pickTimer) {
        clearTimeout(this.pickTimer);
      }
    });
  }

  reload(): void {
    this.open(this.conversationId());
  }

  protected setDraft(text: string): void {
    this.draft.set(text);
    this.live.setDraft(this.draftKey(), text);
  }

  protected send(): void {
    const text = this.draft().trim();
    if (!text) {
      return;
    }
    const pending: PendingMessage = {
      clientMessageId: newClientMessageId(),
      conversationId: this.conversationId(),
      bodyText: text,
      requestId: this.attached()?.id ?? null,
      state: 'sending',
      errorCode: null,
    };
    this.setDraft('');
    this.attached.set(null);
    this.picking.set(false);
    this.deliver(pending);
  }

  protected retry(p: PendingMessage): void {
    this.deliver({ ...p, state: 'sending', errorCode: null });
  }

  protected discard(p: PendingMessage): void {
    this.live.removePending(p.clientMessageId);
    // The text goes back to the composer rather than being lost.
    if (!this.draft()) {
      this.setDraft(p.bodyText);
    }
  }

  protected loadOlder(): void {
    const oldest = this.messages()[0];
    if (!oldest) {
      return;
    }
    const log = this.log()?.nativeElement;
    const fromBottom = log ? log.scrollHeight - log.scrollTop : 0;
    this.loadingOlder.set(true);
    this.api.messages(this.conversationId(), { beforeId: oldest.id }).subscribe({
      next: (page) => {
        this.messages.update((list) => mergeMessages(list, page.items));
        this.hasOlder.set(page.hasOlder);
        this.loadingOlder.set(false);
        // Keep the message the reader was looking at in place.
        afterNextRender(() => log && (log.scrollTop = log.scrollHeight - fromBottom), { injector: this.injector });
      },
      error: () => this.loadingOlder.set(false),
    });
  }

  protected searchRequests(text: string): void {
    this.pickQuery.set(text);
    if (this.pickTimer) {
      clearTimeout(this.pickTimer);
    }
    this.pickTimer = setTimeout(() => {
      const query = (scope: 'mine' | 'visible') =>
        this.requestsApi
          .list({ scope, status: null, q: text.trim() || null, sort: 'updatedAt', direction: 'desc', page: 0, size: 5 })
          .pipe(
            map((page) => page.items),
            catchError(() => of([] as RequestListItem[])),
          );
      forkJoin([query('mine'), query('visible')]).subscribe(([mine, visible]) => {
        const byId = new Map<number, RequestListItem>();
        [...mine, ...visible].forEach((r) => byId.set(r.id, r));
        this.pickResults.set([...byId.values()].slice(0, 6));
      });
    }, 250);
  }

  protected attach(request: RequestListItem): void {
    this.attached.set(request);
    this.picking.set(false);
    afterNextRender(() => this.host.nativeElement.querySelector<HTMLTextAreaElement>('textarea')?.focus(), {
      injector: this.injector,
    });
  }

  protected dayOf(m: Message): string {
    return m.createdAt.slice(0, 10);
  }

  protected systemText(m: Message): string {
    return m.bodyText === 'PARTICIPANT_ADDED' ? 'internalMessaging.system.added' : 'internalMessaging.system.started';
  }

  protected systemParams(m: Message): Record<string, string> {
    return { actor: m.sender.displayName, person: m.subject?.displayName ?? '' };
  }

  private open(id: number): void {
    this.state.set('loading');
    this.messages.set([]);
    this.lastRead = null;
    this.draft.set(this.live.draft(this.draftKey(id)));
    this.attached.set(null);
    this.picking.set(false);
    this.notice.set(null);
    forkJoin({ detail: this.api.conversation(id), page: this.api.messages(id) }).subscribe({
      next: ({ detail, page }) => {
        if (id !== this.conversationId()) {
          return;
        }
        this.lastRead = detail.lastReadMessageId;
        this.messages.set(mergeMessages([], page.items));
        this.hasOlder.set(page.hasOlder);
        this.state.set('ready');
        this.loaded.emit(detail);
        this.afterRender(true);
      },
      error: (error: unknown) => {
        const status = toApiProblem(error).status;
        this.state.set(status === 404 || status === 403 ? 'not-found' : 'error');
      },
    });
  }

  /** Asks the database for everything after the newest message held; repeats while full pages come back. */
  private fetchNewer(): void {
    if (this.fetchingNewer) {
      return;
    }
    const after = newestId(this.messages());
    if (after === null) {
      this.reloadLatest();
      return;
    }
    const id = this.conversationId();
    this.fetchingNewer = true;
    this.api.messages(id, { afterId: after }).subscribe({
      next: (page) => {
        this.fetchingNewer = false;
        if (id !== this.conversationId() || page.items.length === 0) {
          return;
        }
        const nearBottom = this.isNearBottom();
        this.messages.update((list) => mergeMessages(list, page.items));
        this.afterRender(nearBottom);
        if (page.items.length >= 40) {
          this.fetchNewer();
        }
      },
      error: (error: unknown) => {
        this.fetchingNewer = false;
        if (toApiProblem(error).status === 404) {
          // Access was withdrawn meanwhile: show it, and keep nothing on screen.
          this.messages.set([]);
          this.state.set('not-found');
        }
      },
    });
  }

  private reloadLatest(): void {
    const id = this.conversationId();
    this.api.messages(id).subscribe({
      next: (page) => {
        if (id === this.conversationId()) {
          this.messages.update((list) => mergeMessages(list, page.items));
          this.hasOlder.set(page.hasOlder);
          this.afterRender(true);
        }
      },
      error: () => undefined,
    });
  }

  private deliver(p: PendingMessage): void {
    this.live.addPending(p);
    this.afterRender(true);
    this.api.send(p.conversationId, p.bodyText, p.clientMessageId, p.requestId).subscribe({
      next: (result) => {
        this.live.removePending(p.clientMessageId);
        if (p.conversationId === this.conversationId()) {
          this.messages.update((list) => mergeMessages(list, [result.message]));
          if (result.cardWithheld) {
            this.notice.set('internalMessaging.composer.cardWithheld');
          }
          this.afterRender(true);
        }
      },
      error: (error: unknown) => {
        const problem = toApiProblem(error);
        this.live.addPending({ ...p, state: 'failed', errorCode: problem.code });
      },
    });
  }

  private afterRender(scrollToEnd: boolean): void {
    afterNextRender(
      () => {
        const log = this.log()?.nativeElement;
        if (scrollToEnd && log) {
          log.scrollTop = log.scrollHeight;
        }
        this.observeEnd();
        this.markReadIfSeen();
      },
      { injector: this.injector },
    );
  }

  private observeEnd(): void {
    const end = this.end()?.nativeElement;
    if (!end || typeof IntersectionObserver === 'undefined') {
      this.endVisible = true;
      return;
    }
    this.observer?.disconnect();
    this.observer = new IntersectionObserver((entries) => {
      this.endVisible = entries.some((e) => e.isIntersecting);
      this.markReadIfSeen();
    });
    this.observer.observe(end);
  }

  /** Sends the read marker for the newest message on screen, only when the reader can actually see it. */
  private markReadIfSeen(): void {
    if (this.state() !== 'ready' || document.visibilityState !== 'visible' || !this.endVisible) {
      return;
    }
    const marker = readMarkerFor(this.messages(), this.lastRead);
    if (marker === null) {
      return;
    }
    this.lastRead = marker;
    this.api.markRead(this.conversationId(), marker).subscribe({
      next: () => this.live.refreshUnread(),
      error: () => undefined,
    });
  }

  private isNearBottom(): boolean {
    const log = this.log()?.nativeElement;
    return !log || log.scrollHeight - log.scrollTop - log.clientHeight < 120;
  }

  private draftKey(id = this.conversationId()): string {
    return 'conversation:' + id;
  }
}
