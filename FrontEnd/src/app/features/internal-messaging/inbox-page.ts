import { BreakpointObserver } from '@angular/cdk/layout';
import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { Subject, debounceTime, map } from 'rxjs';
import { toApiProblem } from '../../core/api/api-problem';
import { LanguageService } from '../../core/i18n/language.service';
import { Button } from '../../shared/ui/button';
import { EmptyState } from '../../shared/ui/empty-state';
import { ErrorState } from '../../shared/ui/error-state';
import { Icon } from '../../shared/ui/icon';
import { Skeleton } from '../../shared/ui/skeleton';
import { InternalMessagingApi } from './internal-messaging.api';
import { ConversationDetail, ConversationSummary, Person } from './internal-messaging.models';
import { MessageThread } from './message-thread';
import { MessagingLiveService } from './messaging-live.service';
import { RequestCardView } from './request-card';

const ROLE_FILTERS = ['REQUESTER', 'DEPARTMENT_MANAGER', 'BUDGET_APPROVER', 'MANAGEMENT_APPROVER', 'BUYER', 'PURCHASING_MANAGER', 'RECEIVER'];

/**
 * Messages: every conversation of the signed-in employee (one-to-one and request discussions), and the open one.
 * Desktop shows the list beside the thread; phones show one at a time with a back link. The URL holds the open
 * conversation, so it can be bookmarked and the browser's back button works.
 */
@Component({
  selector: 'app-messages-inbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Button, DatePipe, EmptyState, ErrorState, Icon, MessageThread, RequestCardView, RouterLink, Skeleton, TranslatePipe],
  template: `
    <div class="inbox" [class.has-open]="openId() !== null">
      <section class="list-pane" [attr.aria-label]="'internalMessaging.inbox.listLabel' | translate">
        <header class="list-head">
          <h1 class="title" tabindex="-1">{{ 'internalMessaging.inbox.title' | translate }}</h1>
          <button appButton size="small" type="button" (click)="toggleNew()" [attr.aria-expanded]="composingNew()">
            <app-icon [name]="composingNew() ? 'x' : 'plus'" [size]="16" />
            {{ (composingNew() ? 'common.cancel' : 'internalMessaging.inbox.new') | translate }}
          </button>
        </header>

        @if (composingNew()) {
          <div class="directory" role="search" [attr.aria-label]="'internalMessaging.directory.label' | translate">
            <p class="directory-hint">{{ 'internalMessaging.directory.hint' | translate }}</p>
            <label class="field">
              <span class="field-label">{{ 'internalMessaging.directory.name' | translate }}</span>
              <input #personSearch type="search" [value]="dirName()" (input)="dirName.set($any($event.target).value); searchPeople()" />
            </label>
            <div class="field-row">
              <label class="field">
                <span class="field-label">{{ 'internalMessaging.directory.department' | translate }}</span>
                <input type="search" [value]="dirDepartment()" (input)="dirDepartment.set($any($event.target).value); searchPeople()" />
              </label>
              <label class="field">
                <span class="field-label">{{ 'internalMessaging.directory.role' | translate }}</span>
                <select [value]="dirRole()" (change)="dirRole.set($any($event.target).value); searchPeople()">
                  <option value="">{{ 'internalMessaging.directory.anyRole' | translate }}</option>
                  @for (role of roles; track role) {
                    <option [value]="role">{{ 'internalMessaging.roles.' + role | translate }}</option>
                  }
                </select>
              </label>
            </div>
            <ul class="people" [attr.aria-busy]="peopleLoading()">
              @for (p of people(); track p.id) {
                <li>
                  <button type="button" class="person" (click)="startWith(p)" [disabled]="opening()">
                    <span class="avatar" aria-hidden="true">{{ initials(p.displayName) }}</span>
                    <span class="person-text">
                      <span class="person-name" dir="auto">{{ p.displayName }}</span>
                      <span class="person-meta">@if (p.department) {<span>{{ p.department }}</span>}@for (r of p.roles.slice(0, 2); track r) {<span>{{ 'internalMessaging.roles.' + r | translate }}</span>}</span>
                    </span>
                    <app-icon class="go" name="chevron-right" [size]="16" />
                  </button>
                </li>
              } @empty {
                @if (!peopleLoading()) {
                  <li class="muted">{{ 'internalMessaging.directory.none' | translate }}</li>
                }
              }
            </ul>
          </div>
        } @else {
          <div class="search">
            <app-icon class="search-icon" name="search" [size]="16" />
            <label class="visually-hidden" for="conversation-search">{{ 'internalMessaging.inbox.search' | translate }}</label>
            <input id="conversation-search" type="search" [placeholder]="'internalMessaging.inbox.search' | translate" [value]="query()" (input)="query.set($any($event.target).value)" />
          </div>

          @switch (listState()) {
            @case ('loading') {
              <div class="skeletons">
                @for (i of [1, 2, 3, 4]; track i) {
                  <app-skeleton height="3.5rem" />
                }
              </div>
            }
            @case ('error') {
              <app-error-state [heading]="'internalMessaging.inbox.loadError' | translate" [retryLabel]="'common.retry' | translate" (retry)="loadList()" />
            }
            @default {
              <ul class="conversations">
                @for (c of filtered(); track c.id) {
                  <li>
                    <a class="conversation" [routerLink]="['/conversations', c.id]" [class.is-open]="c.id === openId()" [class.is-unread]="c.unread > 0"
                       [attr.aria-current]="c.id === openId() ? 'page' : null">
                      @if (c.kind === 'DIRECT' && c.counterpart) {
                        <span class="avatar" aria-hidden="true">{{ initials(c.counterpart.displayName) }}</span>
                      } @else {
                        <span class="avatar is-request" aria-hidden="true"><app-icon name="file-text" [size]="16" /></span>
                      }
                      <span class="conversation-text">
                        <span class="conversation-top">
                          <span class="conversation-name" dir="auto">{{ conversationName(c) }}</span>
                          @if (c.lastMessageAt) {
                            <time class="when" [attr.datetime]="c.lastMessageAt">{{ c.lastMessageAt | date: dateFormat(c.lastMessageAt) : undefined : language.locale() }}</time>
                          }
                        </span>
                        <span class="preview" dir="auto">
                          @if (c.lastMessageKind === 'SYSTEM') {
                            {{ 'internalMessaging.system.preview' | translate }}
                          } @else if (c.lastMessagePreview) {
                            {{ c.lastMessagePreview }}
                          } @else {
                            {{ 'internalMessaging.inbox.noMessages' | translate }}
                          }
                        </span>
                      </span>
                      @if (c.unread > 0) {
                        <span class="unread tabular">
                          {{ c.unread }}<span class="visually-hidden"> {{ 'internalMessaging.inbox.unread' | translate: { count: c.unread } }}</span>
                        </span>
                      }
                    </a>
                  </li>
                } @empty {
                  <li>
                    <app-empty-state
                      icon="messages"
                      [heading]="(query() ? 'internalMessaging.inbox.noMatch' : 'internalMessaging.inbox.emptyTitle') | translate"
                      [message]="(query() ? '' : 'internalMessaging.inbox.emptyMessage') | translate"
                    />
                  </li>
                }
              </ul>
            }
          }
        }
      </section>

      <section class="thread-pane" [attr.aria-label]="'internalMessaging.inbox.threadLabel' | translate">
        @if (openId(); as id) {
          <header class="thread-head">
            <a class="back" routerLink="/conversations">
              <app-icon name="arrow-left" [size]="16" />
              {{ 'internalMessaging.inbox.back' | translate }}
            </a>
            @if (openDetail(); as d) {
              @if (d.kind === 'DIRECT' && d.counterpart) {
                <span class="avatar" aria-hidden="true">{{ initials(d.counterpart.displayName) }}</span>
                <div class="thread-title">
                  <h2 dir="auto">{{ d.counterpart.displayName }}</h2>
                  <p class="person-meta">@if (d.counterpart.department) {<span>{{ d.counterpart.department }}</span>}@for (r of d.counterpart.roles.slice(0, 2); track r) {<span>{{ 'internalMessaging.roles.' + r | translate }}</span>}</p>
                </div>
              } @else {
                <div class="thread-title">
                  <h2>{{ 'internalMessaging.discussion.title' | translate }}</h2>
                  <p class="person-meta">{{ 'internalMessaging.discussion.participants' | translate: { count: d.participants.length } }}:
                    {{ participantNames(d) }}</p>
                </div>
              }
            }
          </header>
          @if (openDetail()?.kind === 'REQUEST' && openDetail()?.request; as card) {
            <div class="thread-card"><app-request-card [card]="card" /></div>
          }
          <app-message-thread class="thread" [conversationId]="id" [showSender]="openDetail()?.kind !== 'DIRECT'" (loaded)="onLoaded($event)" />
        } @else {
          <app-empty-state class="no-thread" icon="messages" [heading]="'internalMessaging.inbox.pickTitle' | translate" [message]="'internalMessaging.inbox.pickMessage' | translate" />
        }
      </section>
    </div>
  `,
  styleUrl: './inbox-page.scss',
})
export class MessagesInboxPage {
  private readonly api = inject(InternalMessagingApi);
  private readonly live = inject(MessagingLiveService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly language = inject(LanguageService);
  protected readonly roles = ROLE_FILTERS;

  protected readonly listState = signal<'loading' | 'ready' | 'error'>('loading');
  protected readonly conversations = signal<ConversationSummary[]>([]);
  protected readonly query = signal('');
  protected readonly openId = toSignal(this.route.paramMap.pipe(map((p) => (p.get('id') ? Number(p.get('id')) : null))), {
    initialValue: null,
  });
  protected readonly openDetail = signal<ConversationDetail | null>(null);
  protected readonly composingNew = signal(false);
  protected readonly dirName = signal('');
  protected readonly dirDepartment = signal('');
  protected readonly dirRole = signal('');
  protected readonly people = signal<Person[]>([]);
  protected readonly peopleLoading = signal(false);
  protected readonly opening = signal(false);
  private readonly peopleSearch = new Subject<void>();
  private readonly wide = toSignal(inject(BreakpointObserver).observe('(min-width: 64rem)').pipe(map((s) => s.matches)), {
    initialValue: true,
  });

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) {
      return this.conversations();
    }
    return this.conversations().filter((c) =>
      [c.counterpart?.displayName, c.request?.number, c.request?.title, c.lastMessagePreview]
        .filter((v): v is string => !!v)
        .some((v) => v.toLowerCase().includes(q)),
    );
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.loadList();
    this.live.events.pipe(debounceTime(300), takeUntilDestroyed(destroyRef)).subscribe(() => this.loadList(true));
    this.peopleSearch.pipe(debounceTime(250), takeUntilDestroyed(destroyRef)).subscribe(() => this.fetchPeople());
    this.route.paramMap.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => {
      this.openDetail.set(null);
      // An opened conversation is read as it is displayed; the list then shows its new unread count.
      setTimeout(() => this.loadList(true), 800);
    });
  }

  loadList(quiet = false): void {
    if (!quiet) {
      this.listState.set('loading');
    }
    this.api.conversations().subscribe({
      next: (list) => {
        this.conversations.set(list);
        this.listState.set('ready');
      },
      error: (error: unknown) => {
        if (!quiet || this.listState() !== 'ready') {
          this.listState.set(toApiProblem(error).status === 401 ? 'loading' : 'error');
        }
      },
    });
  }

  protected toggleNew(): void {
    this.composingNew.update((v) => !v);
    if (this.composingNew()) {
      this.fetchPeople();
      afterNextRender(() => this.host.nativeElement.querySelector<HTMLInputElement>('.directory input')?.focus(), {
        injector: this.injector,
      });
    }
  }

  protected searchPeople(): void {
    this.peopleSearch.next();
  }

  protected startWith(person: Person): void {
    this.opening.set(true);
    this.api.openDirect(person.id).subscribe({
      next: (detail) => {
        this.opening.set(false);
        this.composingNew.set(false);
        void this.router.navigate(['/conversations', detail.id]).then(() => {
          afterNextRender(() => this.host.nativeElement.querySelector<HTMLTextAreaElement>('.thread textarea')?.focus(), {
            injector: this.injector,
          });
        });
      },
      error: () => this.opening.set(false),
    });
  }

  protected onLoaded(detail: ConversationDetail): void {
    this.openDetail.set(detail);
    if (!this.wide()) {
      afterNextRender(() => this.host.nativeElement.querySelector<HTMLElement>('.thread-title h2')?.focus?.(), {
        injector: this.injector,
      });
    }
  }

  protected conversationName(c: ConversationSummary): string {
    if (c.kind === 'DIRECT') {
      return c.counterpart?.displayName ?? '';
    }
    return c.request?.available ? `${c.request.number ?? ''} · ${c.request.title ?? ''}` : '';
  }

  protected participantNames(d: ConversationDetail): string {
    return d.participants.map((p) => p.displayName).join(', ');
  }

  protected initials(name: string): string {
    const parts = name.replace(/\(.*?\)/g, '').trim().split(/\s+/).filter(Boolean);
    return ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  }

  protected dateFormat(iso: string): string {
    return new Date(iso).toDateString() === new Date().toDateString() ? 'shortTime' : 'd MMM';
  }

  private fetchPeople(): void {
    this.peopleLoading.set(true);
    this.api.directory({ q: this.dirName(), department: this.dirDepartment(), role: this.dirRole() }).subscribe({
      next: (people) => {
        this.people.set(people);
        this.peopleLoading.set(false);
      },
      error: () => {
        this.people.set([]);
        this.peopleLoading.set(false);
      },
    });
  }
}
